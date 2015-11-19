package io.snappydata.dunit.cluster

import java.sql.{Connection, DriverManager}

import com.pivotal.gemfirexd.internal.engine.Misc
import dunit.AvailablePortHelper

import org.apache.spark.sql.SaveMode

/**
  * Created by kneeraj on 29/10/15.
  */
class QueryRoutingDUnitTest(val s: String) extends ClusterManagerTestBase(s) {

  private def getANetConnection(netPort: Int): Connection = {
    val driver = "com.pivotal.gemfirexd.jdbc.ClientDriver"
    Class.forName(driver).newInstance //scalastyle:ignore
    val url = "jdbc:snappydata://localhost:" + netPort + "/"
    DriverManager.getConnection(url)
  }

  def testDummy(): Unit = {

  }

  def _testQueryRouting(): Unit = {
    // Lead is started before other servers are started.
    QueryRoutingDUnitTest.startSnappyServer(locatorPort, props)
    val fullStartArgs = startArgs :+ true.asInstanceOf[AnyRef]
    vm0.invoke(this.getClass, "startSnappyLead", fullStartArgs)
    val netport1 = AvailablePortHelper.getRandomAvailableTCPPort
    QueryRoutingDUnitTest.startNetServer(netport1)

    vm0.invoke(this.getClass, "createTablesAndInsertData")
    val conn = getANetConnection(netport1)
    val s = conn.createStatement()
//    Misc.getMemStore.initExternalCatalog
//    s.execute("select col1 from ColumnTableQR")
//    val rs = s.getResultSet
//    var cnt = 0
//    while(rs.next()) {
//      cnt += 1
//    }
//    assert(cnt == 5)
    s.execute("select * from ColumnTableQR")
    val rs = s.getResultSet
    var cnt = 0
    while(rs.next()) {
      cnt += 1
    }
    assert(cnt == 5)
    val catalog = Misc.getMemStore.getExternalCatalog
    val tt = catalog.isColumnTable("ColumnTableQR")
    println("KN: tt for isColumn for ColumnTable = " + tt)
    QueryRoutingDUnitTest.stopSpark()
    //QueryRoutingDUnitTest.stopAny()
  }
}

case class Data(col1: Int, col2: Int, col3: Int)

/**
  * Since this object derives from ClusterManagerTestUtils
  */
object QueryRoutingDUnitTest extends ClusterManagerTestUtils {
  def createTablesAndInsertData(): Unit = {
    logger.info("KN: spark context = " + sc + " and spark conf = \n" + sc.getConf.toDebugString)
    val snc = org.apache.spark.sql.SnappyContext(sc)
    val tableName: String = "ColumnTableQR"

    val props = Map(
      "url" -> "jdbc:snappydata:;persist-dd=false;route-query=false",
      "driver" -> "com.pivotal.gemfirexd.jdbc.EmbeddedDriver",
      "poolImpl" -> "tomcat",
      "user" -> "app",
      "password" -> "app"
    )

    val data = Seq(Seq(1, 2, 3), Seq(7, 8, 9), Seq(9, 2, 3), Seq(4, 2, 3), Seq(5, 6, 7))
    val rdd = sc.parallelize(data, data.length).map(s => new Data(s(0), s(1), s(2)))
    val dataDF = snc.createDataFrame(rdd)
    snc.createExternalTable(tableName, "column", dataDF.schema, props)
    dataDF.write.format("column").mode(SaveMode.Append).options(props).saveAsTable(tableName)
  }
}

