/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.jdbc;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScriptRunnerTest extends BaseDataTest {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

  @Test
  @Ignore("This fails with HSQLDB 2.0 due to the create index statements in the schema script")
  public void shouldRunScriptsBySendingFullScriptAtOnce() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    Connection conn = ds.getConnection();
    ScriptRunner runner = new ScriptRunner(conn);
    // 全量执行脚本会报错 test push
    runner.setSendFullScript(false);
    runner.setAutoCommit(true);
    runner.setStopOnError(false);
    runner.setErrorLogWriter(null);
    runner.setLogWriter(null);
    runJPetStoreScripts(runner);
    conn.close();
    assertProductsTableExistsAndLoaded();
  }

  @Test
  public void shouldRunScriptsUsingConnection() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    try (Connection conn = ds.getConnection()) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(true);
      runner.setStopOnError(false);
      runner.setErrorLogWriter(null);
      runner.setLogWriter(null);
      runJPetStoreScripts(runner);
    }
    assertProductsTableExistsAndLoaded();
  }

  @Test
  public void shouldRunScriptsUsingProperties() throws Exception {
    Properties props = Resources.getResourceAsProperties(JPETSTORE_PROPERTIES);
    DataSource dataSource = new UnpooledDataSource(
        props.getProperty("driver"),
        props.getProperty("url"),
        props.getProperty("username"),
        props.getProperty("password"));
    ScriptRunner runner = new ScriptRunner(dataSource.getConnection());
    runner.setAutoCommit(true);
    runner.setStopOnError(false);
    runner.setErrorLogWriter(null);
    runner.setLogWriter(null);
    runJPetStoreScripts(runner);
    assertProductsTableExistsAndLoaded();
  }

  @Test
  public void shouldReturnWarningIfEndOfLineTerminatorNotFound() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    String resource = "org/apache/ibatis/jdbc/ScriptMissingEOLTerminator.sql";
    try (Connection conn = ds.getConnection();
         Reader reader = Resources.getResourceAsReader(resource)) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(true);
      runner.setStopOnError(false);
      runner.setErrorLogWriter(null);
      runner.setLogWriter(null);

      try {
        runner.runScript(reader);
        fail("Expected script runner to fail due to missing end of line terminator.");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("end-of-line terminator"));
      }
    }
  }

  @Test
  public void commentAferStatementDelimiterShouldNotCauseRunnerFail() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    String resource = "org/apache/ibatis/jdbc/ScriptCommentAfterEOLTerminator.sql";
    try (Connection conn = ds.getConnection();
         Reader reader = Resources.getResourceAsReader(resource)) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(true);
      runner.setStopOnError(true);
      runner.setErrorLogWriter(null);
      runner.setLogWriter(null);
      runJPetStoreScripts(runner);
      runner.runScript(reader);
    }
  }

  @Test
  public void shouldReturnWarningIfNotTheCurrentDelimiterUsed() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    String resource = "org/apache/ibatis/jdbc/ScriptChangingDelimiterMissingDelimiter.sql";
    try (Connection conn = ds.getConnection();
         Reader reader = Resources.getResourceAsReader(resource)) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(false);
      runner.setStopOnError(true);
      runner.setErrorLogWriter(null);
      runner.setLogWriter(null);
      try {
        runner.runScript(reader);
        fail("Expected script runner to fail due to the usage of invalid delimiter.");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("end-of-line terminator"));
      }
    }
  }

  @Test
  public void changingDelimiterShouldNotCauseRunnerFail() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    String resource = "org/apache/ibatis/jdbc/ScriptChangingDelimiter.sql";
    try (Connection conn = ds.getConnection();
         Reader reader = Resources.getResourceAsReader(resource)) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(false);
      runner.setStopOnError(true);
      runner.setErrorLogWriter(null);
      runner.setLogWriter(null);
      runJPetStoreScripts(runner);
      runner.runScript(reader);
    }
  }

  @Test
  public void testLogging() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    try (Connection conn = ds.getConnection()) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(true);
      runner.setStopOnError(false);
      runner.setErrorLogWriter(null);
      runner.setSendFullScript(false);
      StringWriter sw = new StringWriter();
      PrintWriter logWriter = new PrintWriter(sw);
      runner.setLogWriter(logWriter);

      Reader reader = new StringReader("select userid from account where userid = 'j2ee';");
      runner.runScript(reader);

      assertEquals(
              "select userid from account where userid = 'j2ee'" + LINE_SEPARATOR
                      + LINE_SEPARATOR + "USERID\t" + LINE_SEPARATOR
                      + "j2ee\t" + LINE_SEPARATOR, sw.toString());
    }
  }

  @Test
  public void testLoggingFullScipt() throws Exception {
    DataSource ds = createUnpooledDataSource(JPETSTORE_PROPERTIES);
    try (Connection conn = ds.getConnection()) {
      ScriptRunner runner = new ScriptRunner(conn);
      runner.setAutoCommit(true);
      runner.setStopOnError(false);
      runner.setErrorLogWriter(null);
      runner.setSendFullScript(true);
      StringWriter sw = new StringWriter();
      PrintWriter logWriter = new PrintWriter(sw);
      runner.setLogWriter(logWriter);

      Reader reader = new StringReader("select userid from account where userid = 'j2ee';");
      runner.runScript(reader);

      assertEquals(
              "select userid from account where userid = 'j2ee';" + LINE_SEPARATOR
                      + LINE_SEPARATOR + "USERID\t" + LINE_SEPARATOR
                      + "j2ee\t" + LINE_SEPARATOR, sw.toString());
    }
  }

  private void runJPetStoreScripts(ScriptRunner runner) throws IOException, SQLException {
    runScript(runner, JPETSTORE_DDL);
    runScript(runner, JPETSTORE_DATA);
  }

  private void assertProductsTableExistsAndLoaded() throws IOException, SQLException {
    PooledDataSource ds = createPooledDataSource(JPETSTORE_PROPERTIES);
    try (Connection conn = ds.getConnection()) {
      SqlRunner executor = new SqlRunner(conn);
      List<Map<String, Object>> products = executor.selectAll("SELECT * FROM PRODUCT");
      assertEquals(16, products.size());
    } finally {
      ds.forceCloseAll();
    }
  }

  @Test
  public void shouldAcceptDelimiterVariations() throws Exception {
    Connection conn = mock(Connection.class);
    Statement stmt = mock(Statement.class);
    when(conn.createStatement()).thenReturn(stmt);
    when(stmt.getUpdateCount()).thenReturn(-1);
    ScriptRunner runner = new ScriptRunner(conn);

    String sql = "-- @DELIMITER | \n"
        + "line 1;\n"
        + "line 2;\n"
        + "|\n"
        + "//  @DELIMITER  ;\n"
        + "line 3; \n"
        + "-- //@deLimiTer $  blah\n"
        + "line 4$\n"
        + "// //@DELIMITER %\n"
        + "line 5%\n";
    Reader reader = new StringReader(sql);
    runner.runScript(reader);

    verify(stmt, Mockito.times(1)).execute(eq("line 1;" + LINE_SEPARATOR + "line 2;" + LINE_SEPARATOR + LINE_SEPARATOR));
    verify(stmt, Mockito.times(1)).execute(eq("line 3" + LINE_SEPARATOR));
    verify(stmt, Mockito.times(1)).execute(eq("line 4" + LINE_SEPARATOR));
    verify(stmt, Mockito.times(1)).execute(eq("line 5" + LINE_SEPARATOR));
  }

  @Test
  public void test() throws Exception {
    StringBuilder sb = new StringBuilder();
    StringBuilder sb2 = y(sb);
    assertTrue(sb == sb2);
  }

  private StringBuilder y(StringBuilder sb) {
    sb.append("ABC");
    return sb;
  }

  @Test
  public void shouldAcceptMultiCharDelimiter() throws Exception {
    Connection conn = mock(Connection.class);
    Statement stmt = mock(Statement.class);
    when(conn.createStatement()).thenReturn(stmt);
    when(stmt.getUpdateCount()).thenReturn(-1);
    ScriptRunner runner = new ScriptRunner(conn);

    String sql = "-- @DELIMITER || \n"
        + "line 1;\n"
        + "line 2;\n"
        + "||\n"
        + "//  @DELIMITER  ;\n"
        + "line 3; \n";
    Reader reader = new StringReader(sql);
    runner.runScript(reader);

    verify(stmt, Mockito.times(1)).execute(eq("line 1;" + LINE_SEPARATOR + "line 2;" + LINE_SEPARATOR + LINE_SEPARATOR));
    verify(stmt, Mockito.times(1)).execute(eq("line 3" + LINE_SEPARATOR));
  }
}
