package org.mariadb.jdbc.integration.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.Statement;

public class DecimalCodecTest extends CommonCodecTest {
  @AfterAll
  public static void after2() throws SQLException {
    sharedConn.createStatement().execute("DROP TABLE DecimalCodec");
  }

  @BeforeAll
  public static void beforeAll2() throws SQLException {
    Statement stmt = sharedConn.createStatement();
    stmt.execute("DROP TABLE IF EXISTS DecimalCodec");
    stmt.execute("DROP TABLE IF EXISTS DecimalCodec2");
    stmt.execute(
        "CREATE TABLE DecimalCodec (t1 DECIMAL(10,0), t2 DECIMAL(10,6), t3 DECIMAL(10,3), t4 DECIMAL(10,0))");
    stmt.execute(
        "CREATE TABLE DecimalCodec2 (t1 DECIMAL(10,0), t2 DECIMAL(10,6), t3 DECIMAL(10,3), t4 DECIMAL(10,0))");
    stmt.execute("INSERT INTO DecimalCodec VALUES (0, 105.21, -1.6, null)");
  }

  private ResultSet get() throws SQLException {
    Statement stmt = sharedConn.createStatement();
    ResultSet rs =
        stmt.executeQuery(
            "select t1 as t1alias, t2 as t2alias, t3 as t3alias, t4 as t4alias from DecimalCodec");
    assertTrue(rs.next());
    return rs;
  }

  private ResultSet getPrepare() throws SQLException {
    PreparedStatement stmt =
        sharedConn.prepareStatement(
            "select t1 as t1alias, t2 as t2alias, t3 as t3alias, t4 as t4alias from DecimalCodec"
                + " WHERE 1 > ?");
    stmt.closeOnCompletion();
    stmt.setInt(1, 0);
    ResultSet rs = stmt.executeQuery();
    assertTrue(rs.next());
    return rs;
  }

  @Test
  public void getObject() throws SQLException {
    getObject(get());
  }

  @Test
  public void getObjectPrepare() throws SQLException {
    getObject(getPrepare());
  }

  public void getObject(ResultSet rs) throws SQLException {
    assertEquals(BigDecimal.ZERO, rs.getObject(1));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("105.210000"), rs.getObject(2));
    assertEquals(new BigDecimal("105.210000"), rs.getObject("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("-1.600"), rs.getObject(3));
    assertFalse(rs.wasNull());
    assertNull(rs.getObject(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getObjectType() throws Exception {
    getObjectType(get());
  }

  @Test
  public void getObjectTypePrepare() throws Exception {
    getObjectType(getPrepare());
  }

  public void getObjectType(ResultSet rs) throws Exception {
    testObject(rs, Integer.class, Integer.valueOf(0));
    testObject(rs, String.class, "0");
    testObject(rs, Long.class, Long.valueOf(0));
    testObject(rs, Short.class, Short.valueOf((short) 0));
    testObject(rs, BigDecimal.class, BigDecimal.valueOf(0));
    testObject(rs, BigInteger.class, BigInteger.valueOf(0));
    testObject(rs, Double.class, Double.valueOf(0));
    testObject(rs, Float.class, Float.valueOf(0));
    testObject(rs, Byte.class, Byte.valueOf((byte) 0));
    testErrObject(rs, byte[].class);
    testErrObject(rs, Date.class);
    testErrObject(rs, Time.class);
    testErrObject(rs, Timestamp.class);
    testErrObject(rs, java.util.Date.class);
    testErrObject(rs, LocalDate.class);
    testErrObject(rs, ZonedDateTime.class);
    testErrObject(rs, OffsetDateTime.class);
    testErrObject(rs, LocalDateTime.class);
    testErrObject(rs, OffsetTime.class);
    testObject(rs, Boolean.class, Boolean.FALSE);
    testErrObject(rs, Clob.class);
    testErrObject(rs, NClob.class);
    testErrObject(rs, InputStream.class);
    testErrObject(rs, Reader.class);
  }

  @Test
  public void getString() throws SQLException {
    getString(get());
  }

  @Test
  public void getStringPrepare() throws SQLException {
    getString(getPrepare());
  }

  public void getString(ResultSet rs) throws SQLException {
    assertEquals("0", rs.getString(1));
    assertFalse(rs.wasNull());
    assertEquals("105.210000", rs.getString(2));
    assertEquals("105.210000", rs.getString("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals("-1.600", rs.getString(3));
    assertFalse(rs.wasNull());
    assertNull(rs.getString(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getNString() throws SQLException {
    getNString(get());
  }

  @Test
  public void getNStringPrepare() throws SQLException {
    getNString(getPrepare());
  }

  public void getNString(ResultSet rs) throws SQLException {
    assertEquals("0", rs.getNString(1));
    assertFalse(rs.wasNull());
    assertEquals("105.210000", rs.getNString(2));
    assertEquals("105.210000", rs.getNString("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals("-1.600", rs.getNString(3));
    assertFalse(rs.wasNull());
    assertNull(rs.getNString(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getBoolean() throws SQLException {
    getBoolean(get());
  }

  @Test
  public void getBooleanPrepare() throws SQLException {
    getBoolean(getPrepare());
  }

  public void getBoolean(ResultSet rs) throws SQLException {
    assertFalse(rs.getBoolean(1));
    assertFalse(rs.wasNull());
    assertTrue(rs.getBoolean(2));
    assertTrue(rs.getBoolean("t2alias"));
    assertFalse(rs.wasNull());
    assertTrue(rs.getBoolean(3));
    assertFalse(rs.wasNull());
    assertFalse(rs.getBoolean(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getByte() throws SQLException {
    getByte(get());
  }

  @Test
  public void getBytePrepare() throws SQLException {
    getByte(getPrepare());
  }

  public void getByte(ResultSet rs) throws SQLException {
    assertEquals((byte) 0, rs.getByte(1));
    assertFalse(rs.wasNull());
    assertEquals((byte) 105, rs.getByte(2));
    assertEquals((byte) 105, rs.getByte("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals((byte) -1, rs.getByte(3));
    assertFalse(rs.wasNull());
    assertEquals((byte) 0, rs.getByte(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getShort() throws SQLException {
    getShort(get());
  }

  @Test
  public void getShortPrepare() throws SQLException {
    getShort(getPrepare());
  }

  public void getShort(ResultSet rs) throws SQLException {
    assertEquals(0, rs.getShort(1));
    assertFalse(rs.wasNull());
    assertEquals(105, rs.getShort(2));
    assertEquals(105, rs.getShort("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(-1, rs.getShort(3));
    assertFalse(rs.wasNull());
    assertEquals(0, rs.getShort(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getInt() throws SQLException {
    getInt(get());
  }

  @Test
  public void getIntPrepare() throws SQLException {
    getInt(getPrepare());
  }

  public void getInt(ResultSet rs) throws SQLException {
    assertEquals(0, rs.getInt(1));
    assertFalse(rs.wasNull());
    assertEquals(105, rs.getInt(2));
    assertEquals(105, rs.getInt("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(-1, rs.getInt(3));
    assertFalse(rs.wasNull());
    assertEquals(0, rs.getInt(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getLong() throws SQLException {
    getLong(get());
  }

  @Test
  public void getLongPrepare() throws SQLException {
    getLong(getPrepare());
  }

  public void getLong(ResultSet rs) throws SQLException {
    assertEquals(0L, rs.getLong(1));
    assertFalse(rs.wasNull());
    assertEquals(105L, rs.getLong(2));
    assertEquals(105L, rs.getLong("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(-1L, rs.getLong(3));
    assertFalse(rs.wasNull());
    assertEquals(0L, rs.getLong(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getFloat() throws SQLException {
    getFloat(get());
  }

  @Test
  public void getFloatPrepare() throws SQLException {
    getFloat(getPrepare());
  }

  public void getFloat(ResultSet rs) throws SQLException {
    assertEquals(0F, rs.getFloat(1));
    assertFalse(rs.wasNull());
    assertEquals(105.21F, rs.getFloat(2));
    assertEquals(105.21F, rs.getFloat("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(-1.6F, rs.getFloat(3));
    assertFalse(rs.wasNull());
    assertEquals(0F, rs.getFloat(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getDouble() throws SQLException {
    getDouble(get());
  }

  @Test
  public void getDoublePrepare() throws SQLException {
    getDouble(getPrepare());
  }

  public void getDouble(ResultSet rs) throws SQLException {
    assertEquals(0D, rs.getDouble(1));
    assertFalse(rs.wasNull());
    assertEquals(105.21D, rs.getDouble(2));
    assertEquals(105.21D, rs.getDouble("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(-1.6D, rs.getDouble(3));
    assertFalse(rs.wasNull());
    assertEquals(0D, rs.getDouble(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getBigDecimal() throws SQLException {
    getBigDecimal(get());
  }

  @Test
  public void getBigDecimalPrepare() throws SQLException {
    getBigDecimal(getPrepare());
  }

  public void getBigDecimal(ResultSet rs) throws SQLException {
    assertEquals(BigDecimal.ZERO, rs.getBigDecimal(1));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("105.210000"), rs.getBigDecimal(2));
    assertEquals(new BigDecimal("105.210000"), rs.getBigDecimal("t2alias"));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("-1.600"), rs.getBigDecimal(3));
    assertFalse(rs.wasNull());
    assertNull(rs.getBigDecimal(4));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getBigDecimalScale() throws SQLException {
    getBigDecimalScale(get());
  }

  @Test
  public void getBigDecimalScalePrepare() throws SQLException {
    getBigDecimalScale(getPrepare());
  }

  @SuppressWarnings("deprecation")
  public void getBigDecimalScale(ResultSet rs) throws SQLException {
    assertEquals(new BigDecimal("0.0"), rs.getBigDecimal(1, 1));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("0.0"), rs.getBigDecimal("t1alias", 1));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("105.2"), rs.getBigDecimal(2, 1));
    assertEquals(new BigDecimal("105.2"), rs.getBigDecimal("t2alias", 1));
    assertFalse(rs.wasNull());
    assertEquals(new BigDecimal("-1.6"), rs.getBigDecimal(3, 1));
    assertFalse(rs.wasNull());
    assertNull(rs.getBigDecimal(4, 1));
    assertTrue(rs.wasNull());
  }

  @Test
  public void getDate() throws SQLException {
    getDate(get());
  }

  @Test
  public void getDatePrepare() throws SQLException {
    getDate(getPrepare());
  }

  public void getDate(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class, () -> rs.getDate(1), "Data type DECIMAL cannot be decoded as Date");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getDate("t1alias"),
        "Data type DECIMAL cannot be decoded as Date");
  }

  @Test
  public void getTime() throws SQLException {
    getTime(get());
  }

  @Test
  public void getTimePrepare() throws SQLException {
    getTime(getPrepare());
  }

  public void getTime(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class, () -> rs.getTime(1), "Data type DECIMAL cannot be decoded as Time");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getTime("t1alias"),
        "Data type DECIMAL cannot be decoded as Time");
  }

  @Test
  public void getTimestamp() throws SQLException {
    getTimestamp(get());
  }

  @Test
  public void getTimestampPrepare() throws SQLException {
    getTimestamp(getPrepare());
  }

  public void getTimestamp(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getTimestamp(1),
        "Data type DECIMAL cannot be decoded as Timestamp");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getTimestamp("t1alias"),
        "Data type DECIMAL cannot be decoded as Timestamp");
  }

  @Test
  public void getAsciiStream() throws SQLException {
    getAsciiStream(get());
  }

  @Test
  public void getAsciiStreamPrepare() throws SQLException {
    getAsciiStream(getPrepare());
  }

  public void getAsciiStream(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getAsciiStream(1),
        "Data type DECIMAL cannot be decoded as Stream");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getAsciiStream("t1alias"),
        "Data type DECIMAL cannot be decoded as Stream");
  }

  @Test
  public void getUnicodeStream() throws SQLException {
    getUnicodeStream(get());
  }

  @Test
  public void getUnicodeStreamPrepare() throws SQLException {
    getUnicodeStream(getPrepare());
  }

  @SuppressWarnings("deprecation")
  public void getUnicodeStream(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getUnicodeStream(1),
        "Data type DECIMAL cannot be decoded as Stream");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getUnicodeStream("t1alias"),
        "Data type DECIMAL cannot be decoded as Stream");
  }

  @Test
  public void getBinaryStream() throws SQLException {
    getBinaryStream(get());
  }

  @Test
  public void getBinaryStreamPrepare() throws SQLException {
    getBinaryStream(getPrepare());
  }

  public void getBinaryStream(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getBinaryStream(1),
        "Data type DECIMAL cannot be decoded as Stream");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getBinaryStream("t1alias"),
        "Data type DECIMAL cannot be decoded as Stream");
  }

  @Test
  public void getBytes() throws SQLException {
    getBytes(get());
  }

  @Test
  public void getBytesPrepare() throws SQLException {
    getBytes(getPrepare());
  }

  public void getBytes(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getBytes(1),
        "Data type DECIMAL cannot be decoded as byte[]");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getBytes("t1alias"),
        "Data type DECIMAL cannot be decoded as byte[]");
  }

  @Test
  public void getCharacterStream() throws SQLException {
    getCharacterStream(get());
  }

  @Test
  public void getCharacterStreamPrepare() throws SQLException {
    getCharacterStream(getPrepare());
  }

  public void getCharacterStream(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getCharacterStream(1),
        "Data type DECIMAL cannot be decoded as Reader");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getCharacterStream("t1alias"),
        "Data type DECIMAL cannot be decoded as Reader");
  }

  @Test
  public void getNCharacterStream() throws SQLException {
    getNCharacterStream(get());
  }

  @Test
  public void getNCharacterStreamPrepare() throws SQLException {
    getNCharacterStream(getPrepare());
  }

  public void getNCharacterStream(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getNCharacterStream(1),
        "Data type DECIMAL cannot be decoded as Reader");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getNCharacterStream("t2alias"),
        "Data type DECIMAL cannot be decoded as Reader");
  }

  @Test
  public void getRef() throws SQLException {
    getRef(get());
  }

  @Test
  public void getRefPrepare() throws SQLException {
    getRef(getPrepare());
  }

  public void getRef(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLException.class, () -> rs.getRef(1), "Method ResultSet.getRef not supported");
    assertThrowsContains(
        SQLException.class, () -> rs.getRef("t2alias"), "Method ResultSet.getRef not supported");
  }

  @Test
  public void getBlob() throws SQLException {
    getBlob(get());
  }

  @Test
  public void getBlobPrepare() throws SQLException {
    getBlob(getPrepare());
  }

  public void getBlob(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getCharacterStream(1),
        "Data type DECIMAL cannot be decoded as Reader");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getCharacterStream("t1alias"),
        "Data type DECIMAL cannot be decoded as Reader");
  }

  @Test
  public void getClob() throws SQLException {
    getClob(get());
  }

  @Test
  public void getClobPrepare() throws SQLException {
    getClob(getPrepare());
  }

  public void getClob(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class, () -> rs.getClob(1), "Data type DECIMAL cannot be decoded as Clob");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getClob("t1alias"),
        "Data type DECIMAL cannot be decoded as Clob");
  }

  @Test
  public void getNClob() throws SQLException {
    getNClob(get());
  }

  @Test
  public void getNClobPrepare() throws SQLException {
    getNClob(getPrepare());
  }

  public void getNClob(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getNClob(1),
        "Data type DECIMAL cannot be decoded as Clob");
    assertThrowsContains(
        SQLDataException.class,
        () -> rs.getNClob("t1alias"),
        "Data type DECIMAL cannot be decoded as Clob");
  }

  @Test
  public void getURL() throws SQLException {
    getURL(get());
  }

  @Test
  public void getURLPrepare() throws SQLException {
    getURL(getPrepare());
  }

  public void getURL(ResultSet rs) throws SQLException {
    assertThrowsContains(
        SQLSyntaxErrorException.class, () -> rs.getURL(1), "Could not parse '0' as URL");
    assertThrowsContains(
        SQLSyntaxErrorException.class, () -> rs.getURL("t1alias"), "Could not parse '0' as URL");
  }

  @Test
  public void getMetaData() throws SQLException {
    ResultSet rs = get();
    ResultSetMetaData meta = rs.getMetaData();
    assertEquals("DECIMAL", meta.getColumnTypeName(1));
    assertEquals(sharedConn.getCatalog(), meta.getCatalogName(1));
    assertEquals("java.math.BigDecimal", meta.getColumnClassName(1));
    assertEquals("t1alias", meta.getColumnLabel(1));
    assertEquals("t1", meta.getColumnName(1));
    assertEquals(Types.DECIMAL, meta.getColumnType(1));
    assertEquals(4, meta.getColumnCount());
    assertEquals(10, meta.getPrecision(1));
    assertEquals(0, meta.getScale(1));
    assertEquals("", meta.getSchemaName(1));
    assertEquals(11, meta.getColumnDisplaySize(1));

    assertEquals(10, meta.getPrecision(2));
    assertEquals(6, meta.getScale(2));
    assertEquals("", meta.getSchemaName(2));
    assertEquals(12, meta.getColumnDisplaySize(2));
  }

  @Test
  public void setParameter() throws SQLException {
    try (PreparedStatement prep =
        sharedConn.prepareStatement("INSERT INTO DecimalCodec2 VALUE (?, ?, ?, ?)")) {
      prep.setBigDecimal(1, new BigDecimal("789.123"));
      prep.setBigDecimal(2, new BigDecimal("1789.123456"));
      prep.setNull(3, Types.DECIMAL);
      prep.setObject(4, new BigDecimal("-211789.987987"));
      prep.execute();
      Statement stmt = sharedConn.createStatement();
      ResultSet rs = stmt.executeQuery("SELECT * FROM DecimalCodec2");
      rs.next();
      assertEquals(new BigDecimal("789"), rs.getBigDecimal(1));
      assertEquals(new BigDecimal("1789.123456"), rs.getBigDecimal(2));
      assertNull(rs.getBigDecimal(3));
      assertEquals(new BigDecimal("-211790"), rs.getBigDecimal(4));
      assertThrowsContains(
          SQLException.class,
          () -> prep.setObject(4, this),
          "Type org.mariadb.jdbc.integration.codec.DecimalCodecTest not supported type");
    }
  }
}
