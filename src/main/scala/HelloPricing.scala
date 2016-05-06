
import play.api.libs.json._

import org.apache.poi.hssf.usermodel.HSSFCell
import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.ss.usermodel.CellStyle

import java.io._

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.util.Tables
import com.amazonaws.services.dynamodbv2.document.utils.NameMap

class SkipLoop extends Exception { }

object HelloPricing {


  val ddbRegion  = Regions.US_EAST_1

  var regionCode:String              = ""
  var instanceTypes:List[String]     = List()
  var operationSystems:List[String]  = List()
  var skuOptions:Map[String, String] = Map()

  val purchaseOptions = List(
    "OnDemand"
      ,"1yr All Upfront"
      ,"1yr Partial Upfront"
      ,"1yr No Upfront"
      //,"3yr All Upfront"
      //,"3yr Partial Upfront"
  )

  val LOCATION_TO_REGION = Map(
    "Asia Pacific (Seoul)"         -> "ap-northeast-2"
      ,"Asia Pacific (Singapore)"  -> "ap-southeast-1"
      ,"Asia Pacific (Sydney)"     -> "ap-southeast-2"
      ,"Asia Pacific (Tokyo)"      -> "ap-northeast-1"
      ,"EU (Frankfurt)"            -> "eu-central-1"
      ,"EU (Ireland)"              -> "eu-west-1"
      ,"South America (Sao Paulo)" -> "sa-east-1"
      ,"US East (N. Virginia)"     -> "us-east-1"
      ,"US West (N. California)"   -> "us-west-1"
      ,"US West (Oregon)"          -> "us-west-2"
  )

  val OFFER_CODES = Map(
    "OnDemand"               -> "JRTCKXETXF"
      ,"1yr All Upfront"     -> "6QCMYABX3D"
      ,"1yr Partial Upfront" -> "HU7G6KETJZ"
      ,"1yr No Upfront"      -> "4NA7Y494T4"
      ,"3yr All Upfront"     -> "NQ3QZPMQV9"
      ,"3yr Partial Upfront" -> "38NPMPTW36"
  )

  def initData() : Unit = {

    var dynamoDB:AmazonDynamoDBClient =
      new AmazonDynamoDBClient(new ProfileCredentialsProvider().getCredentials())

    dynamoDB.setRegion(Region.getRegion(ddbRegion))
    
    val expressionAttributeNames = new java.util.HashMap[String, String]()
    expressionAttributeNames.put("#r", "Region")

    val expressionAttributeValues = new java.util.HashMap[String, AttributeValue]()
    expressionAttributeValues.put(":val", new AttributeValue().withS(regionCode))

    var lastKeyEvaluated:java.util.Map[String, AttributeValue] = null
    
    do {

      val scanRequest:ScanRequest = new ScanRequest()
        .withTableName("EC2PricingTable")
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withFilterExpression("#r = :val")
        .withLimit(500)
        .withExclusiveStartKey(lastKeyEvaluated)
      
      val result:ScanResult = dynamoDB.scan(scanRequest)
      lastKeyEvaluated      = result.getLastEvaluatedKey()

      import collection.JavaConverters._
      val items = result.getItems()
      for (item <- items.asScala) {

        val sku = item.get("Sku").getS
        val it  = item.get("InstanceType").getS
        val os  = item.get("OperatingSystem").getS

        operationSystems.length match {
          case 0 =>
            operationSystems = List(os)
          case _ =>
            operationSystems.++=(List(os))
        }

        instanceTypes.length match {
          case 0 =>
            instanceTypes = List(it)
          case _ =>
            instanceTypes.++=(List(it))
        }

        skuOptions += ("%s\u0001%s".format(it, os) -> sku)
      }

    } while (lastKeyEvaluated != null)

     // distinct + sort result
    operationSystems = operationSystems.distinct.sorted
    instanceTypes    = instanceTypes.distinct.sorted
 
  }

  def initExcel(region:String) : Unit = {

    wb    = new HSSFWorkbook()
    sheet = wb.createSheet(region)

    // init row 0 & 1
    var row0 = sheet.createRow(0)
    var row1 = sheet.createRow(1)
    var idxPurchaseOptions = 0

    for (x <- 1 to (purchaseOptions.length * operationSystems.length)) {

      // row 0
      var cell = row0.createCell(x)

      (x % operationSystems.length == 1) match {
        case true =>
          cell.setCellValue(purchaseOptions(idxPurchaseOptions))
          idxPurchaseOptions = idxPurchaseOptions + 1
        case _ =>
          cell.setCellValue("")
          if (x % operationSystems.length == 0) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, (x - (operationSystems.length - 1)), x))
          }
      }

      // row 1
      cell = row1.createCell(x)
      cell.setCellValue(operationSystems((x - 1) % operationSystems.length))
      
    }

  }

  var wb:HSSFWorkbook = null
  var sheet:HSSFSheet = null

  def main(args: Array[String]) : Unit = {

    regionCode = args(0)
    print ("regionCode = %s".format(regionCode))

    regionCode match {
      case "ALL" =>
        for (r <- regionList) {
          processPricingFile(r)
        }
      case _ =>
        processPricingFile(regionCode)
    }
  }

  def processPricingFile(region:String) : Unit = {

    initData()
    initExcel(regionCode)

    try {

      var idxColA = 2 // start from A3

      for (it <- instanceTypes) {

        println (it.toString)

        var row = sheet.createRow(idxColA)

        // set A3:Ax
        var idxCell = 0
        var cell    = row.createCell(idxCell)
        cell.setCellValue(it)

        for (po <- purchaseOptions) {
          for (os <- operationSystems) {

            val idxSku:Option[String] = skuOptions.get("%s\u0001%s".format(it, os))

            idxCell  = idxCell + 1
            var cell = row.createCell(idxCell)

            os match {
              case v if v == "NA" | idxSku == None =>
                cell.setCellValue("")
              case _ =>
                cell.setCellValue(getPrice(idxSku.get, OFFER_CODES.getOrElse(po, "")))
                //cell.setCellValue("%s-%s = %f".format(idxSku.get, OFFER_CODES.getOrElse(po, ""), getPrice(idxSku.get, OFFER_CODES.getOrElse(po, ""))))
            }
          }
        }

        idxColA = idxColA + 1
        //throw new SkipLoop
      }

    } catch {
      case ex:Exception =>
    }

    wirteFile(region)
  }

  val json = Json.parse (
    scala.io.Source.fromFile("conf/EC2_price.json").mkString
  )

  def getPrice(sku:String, offerCode:String) : Double = {

    var price:Double = 0.0
    val idx:String   = "%s.%s".format(sku, offerCode)

    // OnDemand
    if (offerCode == "JRTCKXETXF") {
      price = getOnDemandPrice(sku, idx)
    }

    // 1yr All Upfront
    if (offerCode == "6QCMYABX3D") {
      price = getUpfrontFee(sku, idx)
    }

    // 1yr Partial Upfront
    if (offerCode == "HU7G6KETJZ") {
      val upfrontFee   = getUpfrontFee(sku, idx)
      val hrsToYearPay = getHrsToYearPay(sku, idx)

      price = upfrontFee + hrsToYearPay
    }

    // 1yr No Upfront
    if (offerCode == "4NA7Y494T4") {
      price = getHrsToYearPay(sku, idx)
    }

    // 3yr All Upfront
    if (offerCode == "NQ3QZPMQV9") {
      price = getUpfrontFee(sku, idx)
    }

    // 3yr Partial Upfront
    if (offerCode == "38NPMPTW36") {
      val upfrontFee   = getUpfrontFee(sku, idx)
      val hrsToYearPay = getHrsToYearPay(sku, idx)

      price = upfrontFee + hrsToYearPay
    }

    price
  }

  def getUpfrontFee(sku:String, idx:String) : Double = {

    val upfrontFee = (json \ "terms" \ "Reserved" \ sku \ idx
        \ "priceDimensions" \ "%s.2TG2D8R56U".format(idx)
      \ "pricePerUnit" \ "USD").asOpt[String]

    upfrontFee.getOrElse("0").toDouble
  }

  def getHrsToYearPay(sku:String, idx:String) : Double = {

    val hrsToYearPay = (json \ "terms" \ "Reserved" \ sku \ idx
      \ "priceDimensions" \ "%s.6YS6EN2CT7".format(idx)
      \ "pricePerUnit" \ "USD").asOpt[String]

    hrsToYearPay.getOrElse("0").toDouble * 24 * 365
  }

  def getOnDemandPrice(sku:String, idx:String) : Double = {

    val price = (json \ "terms" \ "OnDemand" \ sku \ idx
      \ "priceDimensions" \ "%s.6YS6EN2CT7".format(idx)
      \ "pricePerUnit" \ "USD").asOpt[String]

    price.getOrElse("0").toDouble
  }

  def wirteFile (r:String) : Unit = {
    val fileOut = new FileOutputStream("/tmp/EC2Pricing_%s.xls".format(r))
    wb.write(fileOut)
    fileOut.flush()
    fileOut.close()
  }

  val regionList = List(
    "ap-northeast-2"
      ,"ap-southeast-1"
      ,"ap-southeast-2"
      ,"ap-northeast-1"
      ,"eu-central-1"
      ,"eu-west-1"
      ,"sa-east-1"
      ,"us-east-1"
      ,"us-west-1"
      ,"us-west-2"
  )

}

