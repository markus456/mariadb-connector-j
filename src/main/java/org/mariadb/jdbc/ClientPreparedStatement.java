// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2024 MariaDB Corporation Ab
package org.mariadb.jdbc;

import static org.mariadb.jdbc.util.constants.Capabilities.*;

import java.sql.*;
import java.util.*;
import org.mariadb.jdbc.client.Completion;
import org.mariadb.jdbc.client.result.Result;
import org.mariadb.jdbc.client.util.ClosableLock;
import org.mariadb.jdbc.export.ExceptionFactory;
import org.mariadb.jdbc.message.ClientMessage;
import org.mariadb.jdbc.message.client.*;
import org.mariadb.jdbc.message.server.OkPacket;
import org.mariadb.jdbc.message.server.PrepareResultPacket;
import org.mariadb.jdbc.util.ClientParser;
import org.mariadb.jdbc.util.ParameterList;
import org.mariadb.jdbc.util.constants.ServerStatus;
import org.mariadb.jdbc.util.timeout.QueryTimeoutHandler;

/**
 * Client side prepare statement. Question mark will be replaced by escaped parameters, client side
 */
public class ClientPreparedStatement extends BasePreparedStatement {
  private final ClientParser parser;

  /**
   * Client prepare statement constructor
   *
   * @param sql command
   * @param con connection
   * @param lock thread safe lock
   * @param canUseServerTimeout can server use timeout
   * @param canUseServerMaxRows can server use max rows
   * @param autoGeneratedKeys must command return automatically generated keys
   * @param resultSetType resultset type
   * @param resultSetConcurrency resultset concurrency
   * @param defaultFetchSize default fetch size
   */
  public ClientPreparedStatement(
      String sql,
      Connection con,
      ClosableLock lock,
      boolean canUseServerTimeout,
      boolean canUseServerMaxRows,
      int autoGeneratedKeys,
      int resultSetType,
      int resultSetConcurrency,
      int defaultFetchSize) {
    super(
        sql,
        con,
        lock,
        canUseServerTimeout,
        canUseServerMaxRows,
        autoGeneratedKeys,
        resultSetType,
        resultSetConcurrency,
        defaultFetchSize);

    boolean noBackslashEscapes =
        (con.getContext().getServerStatus() & ServerStatus.NO_BACKSLASH_ESCAPES) > 0;
    parser = ClientParser.parameterParts(sql, noBackslashEscapes);
    isInsertDuplicate = parser.isInsertDuplicate();
    isCommandInsert = parser.isInsert() && !isInsertDuplicate;
    parameters = new ParameterList(parser.getParamCount());
  }

  /**
   * use additional part for timeout if possible
   *
   * @return pre command for handling timeout
   */
  protected String preSqlCmd() {
    if (queryTimeout != 0 && canUseServerTimeout) {
      if (canUseServerMaxRows && maxRows > 0) {
        return "SET STATEMENT max_statement_time="
            + queryTimeout
            + ", SQL_SELECT_LIMIT="
            + maxRows
            + " FOR ";
      }
      return "SET STATEMENT max_statement_time=" + queryTimeout + " FOR ";
    }
    if (canUseServerMaxRows && maxRows > 0) {
      return "SET STATEMENT SQL_SELECT_LIMIT=" + maxRows + " FOR ";
    }
    return null;
  }

  @SuppressWarnings("try")
  private void executeInternal() throws SQLException {
    checkNotClosed();
    validParameters();
    try (ClosableLock ignore = lock.closeableLock();
        QueryTimeoutHandler ignore2 = this.con.handleTimeout(queryTimeout)) {
      QueryWithParametersPacket query =
          new QueryWithParametersPacket(preSqlCmd(), parser, parameters, localInfileInputStream);
      results =
          con.getClient()
              .execute(
                  query,
                  this,
                  fetchSize,
                  maxRows,
                  resultSetConcurrency,
                  resultSetType,
                  closeOnCompletion,
                  false);
    } catch (SQLException e) {
      results = null;
      currResult = null;
      throw e;
    } finally {
      localInfileInputStream = null;
    }
  }

  private boolean executeInternalPreparedBatch() throws SQLException {
    checkNotClosed();
    checkIfInsertCommand();
    Configuration conf = con.getContext().getConf();
    if (con.getContext().hasServerCapability(STMT_BULK_OPERATIONS)
        && ((isCommandInsert && (conf.useBulkStmts() || conf.useBulkStmtsForInserts()))
            || (!isCommandInsert && conf.useBulkStmts()))
        && batchParameters.size() > 1
        && autoGeneratedKeys != Statement.RETURN_GENERATED_KEYS) {
      executeBatchBulk();
      return isCommandInsert;
    } else {
      boolean possibleLoadLocal = con.getContext().hasClientCapability(LOCAL_FILES);
      if (possibleLoadLocal) {
        String sqlUpper = sql.toUpperCase(Locale.ROOT);
        possibleLoadLocal =
            sqlUpper.contains(" LOCAL ")
                && sqlUpper.contains("LOAD")
                && sqlUpper.contains(" INFILE");
      }
      if (possibleLoadLocal) {
        executeBatchStd();
      } else {
        executeBatchPipeline();
      }
    }
    return false;
  }

  /**
   * Send COM_STMT_PREPARE + X * COM_STMT_BULK_EXECUTE, then read for the all answers
   *
   * @throws SQLException if IOException / Command error
   */
  private void executeBatchBulk() throws SQLException {
    String cmd = escapeTimeout(sql);
    try {
      if (prepareResult == null) {
        ClientMessage[] packets =
            new ClientMessage[] {
              new PreparePacket(cmd), new BulkExecutePacket(null, batchParameters, cmd, null)
            };
        List<Completion> res =
            con.getClient()
                .executePipeline(
                    packets,
                    this,
                    0,
                    maxRows,
                    ResultSet.CONCUR_READ_ONLY,
                    ResultSet.TYPE_FORWARD_ONLY,
                    closeOnCompletion,
                    false);
        // in case of failover, prepare is done in failover, skipping prepare result
        if (res.get(0) instanceof PrepareResultPacket) {
          results = res.subList(1, res.size());
        } else {
          results = res;
        }
      } else {
        results =
            con.getClient()
                .execute(
                    new BulkExecutePacket(prepareResult, batchParameters, cmd, null),
                    this,
                    fetchSize,
                    maxRows,
                    resultSetConcurrency,
                    resultSetType,
                    closeOnCompletion,
                    false);
      }
    } catch (SQLException bue) {
      results = null;
      throw exceptionFactory()
          .createBatchUpdate(Collections.emptyList(), batchParameters.size(), bue);
    }
  }

  /**
   * Send n * COM_QUERY + n * read answer
   *
   * @throws SQLException if IOException / Command error
   */
  private void executeBatchPipeline() throws SQLException {
    ClientMessage[] packets = new ClientMessage[batchParameters.size()];
    for (int i = 0; i < batchParameters.size(); i++) {
      packets[i] = new QueryWithParametersPacket(preSqlCmd(), parser, batchParameters.get(i), null);
    }
    try {
      results =
          con.getClient()
              .executePipeline(
                  packets,
                  this,
                  0,
                  maxRows,
                  ResultSet.CONCUR_READ_ONLY,
                  ResultSet.TYPE_FORWARD_ONLY,
                  closeOnCompletion,
                  false);
    } catch (SQLException bue) {
      results = null;
      throw bue;
    }
  }

  /**
   * Send n * (COM_QUERY + read answer)
   *
   * @throws SQLException if IOException / Command error
   */
  private void executeBatchStd() throws SQLException {
    int i = 0;
    try {
      results = new ArrayList<>();
      for (; i < batchParameters.size(); i++) {
        results.addAll(
            con.getClient()
                .execute(
                    new QueryWithParametersPacket(
                        preSqlCmd(), parser, batchParameters.get(i), localInfileInputStream),
                    this,
                    0,
                    maxRows,
                    ResultSet.CONCUR_READ_ONLY,
                    ResultSet.TYPE_FORWARD_ONLY,
                    closeOnCompletion,
                    false));
      }
    } catch (SQLException bue) {
      BatchUpdateException exception =
          exceptionFactory().createBatchUpdate(results, batchParameters.size(), bue);
      results = null;
      localInfileInputStream = null;
      throw exception;
    }
  }

  /**
   * Executes the SQL statement in this <code>PreparedStatement</code> object, which may be any kind
   * of SQL statement. Some prepared statements return multiple results; the <code>execute</code>
   * method handles these complex statements as well as the simpler form of statements handled by
   * the methods <code>executeQuery</code> and <code>executeUpdate</code>.
   *
   * <p>The <code>execute</code> method returns a <code>boolean</code> to indicate the form of the
   * first result. You must call either the method <code>getResultSet</code> or <code>getUpdateCount
   * </code> to retrieve the result; you must call <code>getMoreResults</code> to move to any
   * subsequent result(s).
   *
   * @return <code>true</code> if the first result is a <code>ResultSet</code> object; <code>false
   *     </code> if the first result is an update count or there is no result
   * @throws SQLException if a database access error occurs; this method is called on a closed
   *     <code>PreparedStatement</code> or an argument is supplied to this method
   * @throws SQLTimeoutException when the driver has determined that the timeout value that was
   *     specified by the {@code setQueryTimeout} method has been exceeded and has at least
   *     attempted to cancel the currently running {@code Statement}
   * @see Statement#execute
   * @see Statement#getResultSet
   * @see Statement#getUpdateCount
   * @see Statement#getMoreResults
   */
  @Override
  public boolean execute() throws SQLException {
    executeInternal();
    currResult = results.remove(0);
    return currResult instanceof Result;
  }

  /**
   * Executes the SQL query in this <code>PreparedStatement</code> object and returns the <code>
   * ResultSet</code> object generated by the query.
   *
   * @return a <code>ResultSet</code> object that contains the data produced by the query; never
   *     <code>null</code>
   * @throws SQLException if a database access error occurs; this method is called on a closed
   *     <code>PreparedStatement</code> or the SQL statement does not return a <code>ResultSet
   *     </code> object
   * @throws SQLTimeoutException when the driver has determined that the timeout value that was
   *     specified by the {@code setQueryTimeout} method has been exceeded and has at least
   *     attempted to cancel the currently running {@code Statement}
   */
  @Override
  public ResultSet executeQuery() throws SQLException {
    executeInternal();
    currResult = results.remove(0);
    if (currResult instanceof Result) {
      return (Result) currResult;
    }
    throw new SQLException(
        "PrepareStatement.executeQuery() command does NOT return a result-set as expected. Either"
            + " use PrepareStatement.execute(), PrepareStatement.executeUpdate(), or correct"
            + " command");
  }

  /**
   * Executes the SQL statement in this <code>PreparedStatement</code> object, which must be an SQL
   * Data Manipulation Language (DML) statement, such as <code>INSERT</code>, <code>UPDATE</code> or
   * <code>DELETE</code>; or an SQL statement that returns nothing, such as a DDL statement.
   *
   * @return either (1) the row count for SQL Data Manipulation Language (DML) statements or (2) 0
   *     for SQL statements that return nothing
   * @throws SQLException if a database access error occurs; this method is called on a closed
   *     <code>PreparedStatement</code> or the SQL statement returns a <code>ResultSet</code> object
   * @throws SQLTimeoutException when the driver has determined that the timeout value that was
   *     specified by the {@code setQueryTimeout} method has been exceeded and has at least
   *     attempted to cancel the currently running {@code Statement}
   */
  @Override
  public int executeUpdate() throws SQLException {
    return (int) executeLargeUpdate();
  }

  /**
   * Executes the SQL statement in this <code>PreparedStatement</code> object, which must be an SQL
   * Data Manipulation Language (DML) statement, such as <code>INSERT</code>, <code>UPDATE</code> or
   * <code>DELETE</code>; or an SQL statement that returns nothing, such as a DDL statement.
   *
   * <p>This method should be used when the returned row count may exceed {@link Integer#MAX_VALUE}.
   *
   * <p>The default implementation will throw {@code UnsupportedOperationException}
   *
   * @return either (1) the row count for SQL Data Manipulation Language (DML) statements or (2) 0
   *     for SQL statements that return nothing
   * @throws SQLException if a database access error occurs; this method is called on a closed
   *     <code>PreparedStatement</code> or the SQL statement returns a <code>ResultSet</code> object
   * @throws SQLTimeoutException when the driver has determined that the timeout value that was
   *     specified by the {@code setQueryTimeout} method has been exceeded and has at least
   *     attempted to cancel the currently running {@code Statement}
   * @since 1.8
   */
  @Override
  public long executeLargeUpdate() throws SQLException {
    executeInternal();
    currResult = results.remove(0);
    if (currResult instanceof Result) {
      throw exceptionFactory()
          .create("the given SQL statement produces an unexpected ResultSet object", "HY000");
    }
    return ((OkPacket) currResult).getAffectedRows();
  }

  private ExceptionFactory exceptionFactory() {
    return con.getExceptionFactory().of(this);
  }

  /**
   * Adds a set of parameters to this <code>PreparedStatement</code> object's batch of commands.
   *
   * @throws SQLException if a database access error occurs or this method is called on a closed
   *     <code>PreparedStatement</code>
   * @see Statement#addBatch
   * @since 1.2
   */
  @Override
  public void addBatch() throws SQLException {
    validParameters();
    if (batchParameters == null) batchParameters = new ArrayList<>();
    batchParameters.add(parameters);
    parameters = parameters.clone();
  }

  /**
   * Validate parameter number according to expected parameter.
   *
   * @throws SQLException if doesn't correspond
   */
  protected void validParameters() throws SQLException {
    for (int i = 0; i < parser.getParamCount(); i++) {
      if (!parameters.containsKey(i)) {
        throw exceptionFactory()
            .create("Parameter at position " + (i + 1) + " is not set", "07004");
      }
    }
  }

  @Override
  public void setQueryTimeout(int seconds) throws SQLException {
    super.setQueryTimeout(seconds);
    if (canUseServerTimeout && prepareResult != null) {
      prepareResult.close(con.getClient());
      prepareResult = null;
    }
  }

  @Override
  public void setMaxRows(int max) throws SQLException {
    super.setMaxRows(max);
    if (canUseServerMaxRows && prepareResult != null) {
      prepareResult.close(con.getClient());
      prepareResult = null;
    }
  }

  @Override
  public void setLargeMaxRows(long max) throws SQLException {
    super.setLargeMaxRows(max);
    if (canUseServerMaxRows && prepareResult != null) {
      prepareResult.close(con.getClient());
      prepareResult = null;
    }
  }

  /**
   * Retrieves a <code>ResultSetMetaData</code> object that contains information about the columns
   * of the <code>ResultSet</code> object that will be returned when this <code>PreparedStatement
   * </code> object is executed.
   *
   * <p>Because a <code>PreparedStatement</code> object is precompiled, it is possible to know about
   * the <code>ResultSet</code> object that it will return without having to execute it.
   * Consequently, it is possible to invoke the method <code>getMetaData</code> on a <code>
   * PreparedStatement</code> object rather than waiting to execute it and then invoking the <code>
   * ResultSet.getMetaData</code> method on the <code>ResultSet</code> object that is returned.
   *
   * @return the description of a <code>ResultSet</code> object's columns or <code>null</code> if
   *     the driver cannot return a <code>ResultSetMetaData</code> object
   * @throws SQLException if a database access error occurs or this method is called on a closed
   *     <code>PreparedStatement</code>
   */
  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    // send COM_STMT_PREPARE
    if (prepareResult == null)
      con.getClient().execute(new PreparePacket(escapeTimeout(sql)), this, true);
    return new org.mariadb.jdbc.client.result.ResultSetMetaData(
        exceptionFactory(), prepareResult.getColumns(), con.getContext().getConf(), false);
  }

  /**
   * Retrieves the number, types and properties of this <code>PreparedStatement</code> object's
   * parameters.
   *
   * @return a <code>ParameterMetaData</code> object that contains information about the number,
   *     types and properties for each parameter marker of this <code>PreparedStatement</code>
   *     object
   * @throws SQLException if a database access error occurs or this method is called on a closed
   *     <code>PreparedStatement</code>
   * @see ParameterMetaData
   * @since 1.4
   */
  @Override
  public java.sql.ParameterMetaData getParameterMetaData() throws SQLException {
    // send COM_STMT_PREPARE
    if (prepareResult == null) {
      try {
        con.getClient().execute(new PreparePacket(escapeTimeout(sql)), this, true);
      } catch (SQLException e) {
        return new SimpleParameterMetaData(exceptionFactory(), parser.getParamCount());
      }
    }
    return new ParameterMetaData(exceptionFactory(), prepareResult.getParameters());
  }

  @Override
  @SuppressWarnings("try")
  public int[] executeBatch() throws SQLException {
    checkNotClosed();
    if (batchParameters == null || batchParameters.isEmpty()) return new int[0];
    try (ClosableLock ignore = lock.closeableLock();
        QueryTimeoutHandler ignore2 = this.con.handleTimeout(queryTimeout)) {
      boolean wasBulkInsert = executeInternalPreparedBatch();

      int[] updates = new int[batchParameters.size()];

      // specific case for BULK INSERT
      // return not Statement.SUCCESS_NO_INFO, but 1
      if (wasBulkInsert) {
        int numberOfResult = 0;
        for (int i = 0; i < results.size(); i++) {
          numberOfResult += (int) ((OkPacket) results.get(i)).getAffectedRows();
        }
        if (numberOfResult == updates.length) {
          Arrays.fill(updates, 1);
          currResult = results.remove(0);
          return updates;
        }
      }

      if (results.size() != batchParameters.size()) {
        for (int i = 0; i < batchParameters.size(); i++) {
          updates[i] = Statement.SUCCESS_NO_INFO;
        }
      } else {
        for (int i = 0; i < updates.length; i++) {
          if (results.get(i) instanceof OkPacket) {
            updates[i] = (int) ((OkPacket) results.get(i)).getAffectedRows();
          } else {
            updates[i] = org.mariadb.jdbc.Statement.SUCCESS_NO_INFO;
          }
        }
      }
      currResult = results.remove(0);
      return updates;

    } catch (SQLException e) {
      results = null;
      currResult = null;
      throw e;
    } finally {
      batchParameters.clear();
    }
  }

  @Override
  @SuppressWarnings("try")
  public long[] executeLargeBatch() throws SQLException {
    checkNotClosed();
    if (batchParameters == null || batchParameters.isEmpty()) return new long[0];
    try (ClosableLock ignore = lock.closeableLock();
        QueryTimeoutHandler ignore2 = this.con.handleTimeout(queryTimeout)) {
      boolean wasBulkInsert = executeInternalPreparedBatch();
      long[] updates = new long[results.size()];

      // specific case for BULK INSERT
      // return not Statement.SUCCESS_NO_INFO, but 1
      if (wasBulkInsert) {
        int numberOfResult = 0;
        for (int i = 0; i < results.size(); i++) {
          numberOfResult += (int) ((OkPacket) results.get(i)).getAffectedRows();
        }
        if (numberOfResult == updates.length) {
          Arrays.fill(updates, 1);
          currResult = results.remove(0);
          return updates;
        }
      }

      for (int i = 0; i < results.size(); i++) {
        updates[i] = ((OkPacket) results.get(i)).getAffectedRows();
      }
      currResult = results.remove(0);
      return updates;

    } catch (SQLException e) {
      results = null;
      currResult = null;
      throw e;
    } finally {
      batchParameters.clear();
    }
  }

  @Override
  @SuppressWarnings("try")
  public void close() throws SQLException {
    if (prepareResult != null) {
      try (ClosableLock ignore = lock.closeableLock()) {
        prepareResult.close(this.con.getClient());
      }
    }
    con.fireStatementClosed(this);
    super.close();
  }

  @Override
  public String toString() {
    return "ClientPreparedStatement{" + super.toString() + '}';
  }
}
