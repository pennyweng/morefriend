package controllers.line

import play.api._
import play.api.mvc._
import play.Logger

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.JavaConversions._

import akka.actor.{ActorSystem, Props}

import redis.RedisServer
import redis.RedisClientPool
import redis.api.Limit
import redis.RedisClient
import play.api.libs.concurrent.Akka
import scala.concurrent.Future

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._
import com.fasterxml.jackson.core.JsonParser.Feature
import scala.util.parsing.json.JSONFormat
import scala.collection.mutable.ListBuffer
import controllers.line.Constants._
import controllers.line.TraceApplication._


case class ReportMessag(rmid : String, cts : String, lnick : String, lid : String, lname : String, code : String, userMsgs : Array[LeaveMsg], lineImg : String, mark : String, status : Int)  extends Ordered[ReportMessag] { 
	 def compare( a: ReportMessag ) = cts.compareTo(a.cts)
}

case class ReportMoneyMessag(rmid : String, cts : String, bank_name : String, bank_code : String, account : String, account_name: String, lname : String, code : String, userMsgs : Array[LeaveMsg], lineImg : String, mark : String, status : Int)  extends Ordered[ReportMoneyMessag] { 
	 def compare( a: ReportMoneyMessag ) = cts.compareTo(a.cts)
}

case class ReportBagMessag(rmid : String, cts : String, postcode : String, address : String, account : String, phone: String, lname : String, code : String, userMsgs : Array[LeaveMsg], lineImg : String, mark : String, status : Int)  extends Ordered[ReportBagMessag] { 
	 def compare( a: ReportBagMessag ) = cts.compareTo(a.cts)
}

object AdminConsole extends Controller {
	implicit val system = Akka.system
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))


	def updateReport() = {
// 		val dd = s"""
// {
// 					"id":"dc3f1782-8d4c-435e-8ab3-11aab9edf41e_F8F77DE606B32B9AA19C52DC90BA3854BED784C4",
// 					"uid":"F8F77DE606B32B9AA19C52DC90BA3854BED784C4",
// 					"lid":"0952788726",
// 					"lnick":"^Only^",
// 					"lname":"BG MEN動動感謝祭",
// 					"code":"dc341eF8F4C458",
// 					"status":2,
// 					"line_img":"https://sdl-stickershop.line.naver.jp/products/0/0/1/4326/LINEStorePC/thumbnail_shop.png",
// 					"cts":1438258570952,
// 					"mark":""
// 				}
// 		"""

// 		redis.hset(REDIS_KEY_GIFT_REPORT_LINE, "dc3f1782-8d4c-435e-8ab3-11aab9edf41e_F8F77DE606B32B9AA19C52DC90BA3854BED784C4", dd)

		// redis.hset(REDIS_KEY_GIFT_REPORT_LINE_TOPIC, "aef5fd2d-812e-490a-8534-301174bf4528_BDBB34D3D11B0C9BEF530E31559FAD17484959DC", dd)
	}


	def getLines(searchLid : String, searchStatus : Int, isLine : Boolean = true) = {
		val (reportTable, giftTable)  = if(isLine) (REDIS_KEY_GIFT_REPORT_LINE, REDIS_KEY_GIFT_LINE)
			else (REDIS_KEY_GIFT_REPORT_LINE_TOPIC, REDIS_KEY_GIFT_LINE_TOPIC)

		val process1 = for {
			all <- redis.hgetall(reportTable) //Future[Map[String, R]]
			lineAll <- redis.hgetall(giftTable)
		} yield {
			all.flatMap { k =>
				println("k:" + k._2.utf8String)
				val jj = mapper.readTree(k._2.utf8String)
				val status = jj.get("status").asInt
				// println("status:" + status)

				// if(status == 1 || status == 2 || status == 3) {
				val lgid = k._1.split("_")(0)
				val uid = k._1.split("_")(1) 
				if(lineAll.contains(lgid)) {
					val linej = mapper.readTree(lineAll(lgid).utf8String)
					val ssc = if(searchStatus == 1) 
								List(1,2,3) 
							  else if(searchStatus == 2)	
							     List(1)
							  else if(searchStatus == 3)
							  	List(2)
							  else if(searchStatus == 4)
							  	List(3) 	   
							  else if(searchStatus == 5) 
								List(4) 
							  else if(searchStatus == 7) 
								List(5) 
							else List(1,2,3,4,5)


					if(searchLid != "" && jj.get("lid").asText != searchLid) None
					else if(!ssc.contains(status)) None
					else
					Some((k._1, (k._1, 
						jj.get("cts").asText, 
						jj.get("lnick").asText, 
						jj.get("lid").asText, 
						linej.get("name").asText, 
						jj.get("code").asText, 
						linej.get("img").asText,
						jj.get("mark").asText, status)))
				} else None
			}.toMap
		}

		for {
			s <- process1
			msgs <- if(s.size > 0) redis.hmget(REDIS_KEY_GIFT_REPORT_LINE_MSG, s.keys.toList : _*) else Future.successful(Seq())//Future[Seq[Option[R]]]
		} yield {
			val ll = s.keys.toList
			val ss = (0 until ll.size).map { f =>
				val key = ll(f)
				val mss = if(msgs(f).isDefined) {
					mapper.readValue[Array[LeaveMsg]](msgs(f).get.utf8String, classOf[Array[LeaveMsg]])
				} else Array[LeaveMsg]()
				ReportMessag(s(key)._1, s(key)._2, s(key)._3, s(key)._4, s(key)._5, s(key)._6, mss, s(key)._7, s(key)._8, s(key)._9)	
			}.toArray
			scala.util.Sorting.quickSort(ss)
			ss
		}
	}

	def indexLineTopic(searchLid : String, searchStatus : Int) = Action.async {
		getLines(searchLid, searchStatus, false).map { ss =>
			Ok(views.html.admin_index_topic(ss, searchLid, searchStatus))
		}
	}


	def index(searchLid : String, searchStatus : Int) = Action.async {
		getLines(searchLid, searchStatus).map { ss =>
			Ok(views.html.admin_index(ss, searchLid, searchStatus))
		}
	}

	def indexMoney(searchName : String, searchStatus : Int) = Action.async {
		getMoney(searchName, searchStatus).map { ss =>
			Ok(views.html.admin_money_index(ss, searchName, searchStatus))
		}
	}

	def indexBag(searchName : String, searchStatus : Int) = Action.async {
		getBag(searchName, searchStatus).map { ss =>
			Ok(views.html.admin_bag_index(ss, searchName, searchStatus))
		}
	}

	def indexSe(searchName : String, searchStatus : Int) = Action.async {
		getSe(searchName, searchStatus).map { ss =>
			Ok(views.html.admin_se_index(ss, searchName, searchStatus))
		}
	}

	def getSe(searchName : String, searchStatus : Int) = {
		val process1 = for {
			all <- redis.hgetall(REDIS_KEY_GIFT_REPORT_SE) //Future[Map[String, R]]
			lineAll <- redis.hgetall(REDIS_KEY_GIFT_SE)
		} yield {
			all.flatMap { k =>
				val jj = mapper.readTree(k._2.utf8String)
				val status = jj.get("status").asInt
				val lgid = k._1.split("_")(0)
				val uid = k._1.split("_")(1) 
				val linej = mapper.readTree(lineAll(lgid).utf8String)
				// val ssc = if(searchStatus == 1) 
				// 			List(1,2,3) 
				// 			else if(searchStatus == 2) 
				// 			List(4) 
				// 			else List(1,2,3,4,5)


					val ssc = if(searchStatus == 1) 
								List(1,2,3) 
							  else if(searchStatus == 2)	
							     List(1)
							  else if(searchStatus == 3)
							  	List(2)
							  else if(searchStatus == 4)
							  	List(3) 	   
							  else if(searchStatus == 5) 
								List(4) 
								 else if(searchStatus == 7) 
								List(5) 
							else List(1,2,3,4,5)

				if(searchName != "" && jj.get("account").asText != searchName) None
				else if(!ssc.contains(status)) None
				else {
					Some((k._1, (k._1, 
					jj.get("cts").asText, 
					jj.get("postcode").asText, 
					jj.get("address").asText, 
					jj.get("account").asText, 
					jj.get("phone").asText,
					jj.get("lname").asText,
					jj.get("code").asText, 
					linej.get("img").asText,
					jj.get("mark").asText, status)))
				}
			}.toMap
		}

		for {
			s <- process1
			msgs <- if(s.size > 0) redis.hmget(REDIS_KEY_GIFT_REPORT_LINE_MSG, s.keys.toList : _*) else Future.successful(Seq())//Future[Seq[Option[R]]]
		} yield {
			val ll = s.keys.toList
			val ss = (0 until ll.size).map { f =>
				val key = ll(f)
				val mss = if(msgs(f).isDefined) {
					mapper.readValue[Array[LeaveMsg]](msgs(f).get.utf8String, classOf[Array[LeaveMsg]])
				} else Array[LeaveMsg]()
				println("mark:" + s(key)._10)
				ReportBagMessag(s(key)._1, s(key)._2, s(key)._3, s(key)._4, s(key)._5, s(key)._6, s(key)._7, s(key)._8, mss, s(key)._9, s(key)._10, s(key)._11)
			}.toArray
			scala.util.Sorting.quickSort(ss)
			ss
		}
	}


	def getBag(searchName : String, searchStatus : Int) = {
		val process1 = for {
			all <- redis.hgetall(REDIS_KEY_GIFT_REPORT_BAG) //Future[Map[String, R]]
			lineAll <- redis.hgetall(REDIS_KEY_GIFT_BAG)
		} yield {
			all.flatMap { k =>
				val jj = mapper.readTree(k._2.utf8String)
				val status = jj.get("status").asInt
				val lgid = k._1.split("_")(0)
				val uid = k._1.split("_")(1) 
				val linej = mapper.readTree(lineAll(lgid).utf8String)
				// val ssc = if(searchStatus == 1) 
				// 			List(1,2,3) 
				// 			else if(searchStatus == 2) 
				// 			List(4) 
				// 			else List(1,2,3,4,5)

					val ssc = if(searchStatus == 1) 
								List(1,2,3) 
							  else if(searchStatus == 2)	
							     List(1)
							  else if(searchStatus == 3)
							  	List(2)
							  else if(searchStatus == 4)
							  	List(3) 	   
							  else if(searchStatus == 5) 
								List(4) 
								 else if(searchStatus == 7) 
								List(5) 
							else List(1,2,3,4,5)

				if(searchName != "" && jj.get("account").asText != searchName) None
				else if(!ssc.contains(status)) None
				else
				Some((k._1, (k._1, 
					jj.get("cts").asText, 
					jj.get("postcode").asText, 
					jj.get("address").asText, 
					jj.get("account").asText, 
					jj.get("phone").asText,
					jj.get("lname").asText,
					jj.get("code").asText, 
					linej.get("img").asText,
					jj.get("mark").asText, status)))
			}.toMap
		}

		for {
			s <- process1
			msgs <- if(s.size > 0) redis.hmget(REDIS_KEY_GIFT_REPORT_LINE_MSG, s.keys.toList : _*) else Future.successful(Seq())//Future[Seq[Option[R]]]
		} yield {
			val ll = s.keys.toList
			val ss = (0 until ll.size).map { f =>
				val key = ll(f)
				val mss = if(msgs(f).isDefined) {
					mapper.readValue[Array[LeaveMsg]](msgs(f).get.utf8String, classOf[Array[LeaveMsg]])
				} else Array[LeaveMsg]()
				ReportBagMessag(s(key)._1, s(key)._2, s(key)._3, s(key)._4, s(key)._5, s(key)._6, s(key)._7, s(key)._8, mss, s(key)._9, s(key)._10, s(key)._11)
			}.toArray
			scala.util.Sorting.quickSort(ss)
			ss
		}
	}

	def getMoney(searchName : String, searchStatus : Int) = {
		val process1 = for {
			all <- redis.hgetall(REDIS_KEY_GIFT_REPORT_MONEY) //Future[Map[String, R]]
			lineAll <- redis.hgetall(REDIS_KEY_GIFT_MONEY)
		} yield {
			all.flatMap { k =>
				val jj = mapper.readTree(k._2.utf8String)
				val status = jj.get("status").asInt
				val lgid = k._1.split("_")(0)
				val uid = k._1.split("_")(1) 
				val linej = mapper.readTree(lineAll(lgid).utf8String)
				// val ssc = if(searchStatus == 1) 
				// 			List(1,2,3) 
				// 			else if(searchStatus == 2) 
				// 			List(4) 
				// 			else List(1,2,3,4,5)


					val ssc = if(searchStatus == 1) 
								List(1,2,3) 
							  else if(searchStatus == 2)	
							     List(1)
							  else if(searchStatus == 3)
							  	List(2)
							  else if(searchStatus == 4)
							  	List(3) 	   
							  else if(searchStatus == 5) 
								List(4) 
								 else if(searchStatus == 7) 
								List(5) 
							else List(1,2,3,4,5)
							
				if(searchName != "" && jj.get("account_name").asText != searchName) None
				else if(!ssc.contains(status)) None
				else
				Some((k._1, (k._1, 
					jj.get("cts").asText, 
					jj.get("bank_name").asText, 
					jj.get("bank_code").asText, 
					jj.get("account").asText, 
					jj.get("account_name").asText,
					jj.get("lname").asText,
					jj.get("code").asText, 
					linej.get("img").asText,
					jj.get("mark").asText, status)))
			}.toMap
		}

		for {
			s <- process1
			msgs <- if(s.size > 0) redis.hmget(REDIS_KEY_GIFT_REPORT_LINE_MSG, s.keys.toList : _*) else Future.successful(Seq())//Future[Seq[Option[R]]]
		} yield {
			val ll = s.keys.toList
			val ss = (0 until ll.size).map { f =>
				val key = ll(f)
				val mss = if(msgs(f).isDefined) {
					mapper.readValue[Array[LeaveMsg]](msgs(f).get.utf8String, classOf[Array[LeaveMsg]])
				} else Array[LeaveMsg]()
				ReportMoneyMessag(s(key)._1, s(key)._2, s(key)._3, s(key)._4, s(key)._5, s(key)._6, s(key)._7, s(key)._8, mss, s(key)._9, s(key)._10, s(key)._11)
			}.toArray
			scala.util.Sorting.quickSort(ss)
			ss
		}
	}

	def updateBag() = Action.async { request =>
		val body = request.body.asFormUrlEncoded.get
		// body:Map(updatebt -> List(修改), id -> List(96ca3066-ed54-4a94-aecb-85b955edd6a1_50DE4E8F8F8476D79DA9C3CA264357F9B9B52E9F), status -> List(1), mark -> List(), magmsg -> List())
		println("body:" + body)

		val id = body("id")(0)
		val status = 
			if(body("status").size > 0)
				body("status")(0).toInt
			else 1
		val mark = 
			if(body("mark").size > 0)
				body("mark")(0)
			else ""
		val leaveMsg = 
			if(body("magmsg").size > 0)
				body("magmsg")(0)
			else ""
		val searchName = body.get("searchName").getOrElse(List(""))(0)
		val searchStatus = body.get("searchStatus").getOrElse(List("1"))(0).toInt

		// if(status == 5) {
		// 	for {
		// 		k1 <- redis.hdel(REDIS_KEY_GIFT_REPORT_BAG, id)
		// 		k2 <- redis.hdel(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
		// 		ss <- getBag(searchName, searchStatus)				
		// 	} yield { 
		// 		Ok(views.html.admin_bag_index(ss, searchName, searchStatus))
		// 	}
		// } else {
			val k1 = for {
				rm <- redis.hget(REDIS_KEY_GIFT_REPORT_BAG, id)
				ms <- redis.hget(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
			} yield {
				if(rm.isDefined) {
					val jj = mapper.readTree(rm.get.utf8String)
					val existMsg = if(ms.isDefined)
						mapper.readValue[Array[LeaveMsg]](ms.get.utf8String, classOf[Array[LeaveMsg]])
					else Array[LeaveMsg]()

					if(leaveMsg != "") {
						val lmsg = LeaveMsg(leaveMsg, System.currentTimeMillis, 0)
						val newLms = Array(lmsg) ++ existMsg
						redis.hset(REDIS_KEY_GIFT_REPORT_LINE_MSG, id, mapper.writeValueAsString(newLms))
					}

					val result = s"""{
						"id":"${id}",
						"uid":"${jj.get("uid").asText}",
						"postcode":"${jj.get("postcode").asText}",
						"address":"${jj.get("address").asText}",
						"account":"${jj.get("account").asText}",
						"phone":"${jj.get("phone").asText}",
						"lname":"${jj.get("lname").asText}",
						"code":"${jj.get("code").asText}",
						"status":${status},
						"line_img":"${jj.get("line_img").asText}",
						"cts":${jj.get("cts").asLong},
						"mark":"${mark}"
					}"""

					redis.hset(REDIS_KEY_GIFT_REPORT_BAG, id, result)				
				}
			}

			for {
				kk <- k1
				ss <- getBag(searchName, searchStatus)				
			} yield { 
				Ok(views.html.admin_bag_index(ss, searchName, searchStatus))
			}

		// }

	}


	def updateMoney() = Action.async { request =>
		val body = request.body.asFormUrlEncoded.get
		// body:Map(updatebt -> List(修改), id -> List(96ca3066-ed54-4a94-aecb-85b955edd6a1_50DE4E8F8F8476D79DA9C3CA264357F9B9B52E9F), status -> List(1), mark -> List(), magmsg -> List())
		println("body:" + body)

		val id = body("id")(0)
		val status = 
			if(body("status").size > 0)
				body("status")(0).toInt
			else 1
		val mark = 
			if(body("mark").size > 0)
				body("mark")(0)
			else ""
		val leaveMsg = 
			if(body("magmsg").size > 0)
				body("magmsg")(0)
			else ""
		val searchName = body.get("searchName").getOrElse(List(""))(0)
		val searchStatus = body.get("searchStatus").getOrElse(List("1"))(0).toInt

		// if(status == 5) {
		// 	for {
		// 		k1 <- redis.hdel(REDIS_KEY_GIFT_REPORT_MONEY, id)
		// 		k2 <- redis.hdel(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
		// 		ss <- getMoney(searchName, searchStatus)				
		// 	} yield { 
		// 		Ok(views.html.admin_money_index(ss, searchName, searchStatus))
		// 	}
		// } else {
			val k1 = for {
				rm <- redis.hget(REDIS_KEY_GIFT_REPORT_MONEY, id)
				ms <- redis.hget(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
			} yield {
				if(rm.isDefined) {
					val jj = mapper.readTree(rm.get.utf8String)
					val existMsg = if(ms.isDefined)
						mapper.readValue[Array[LeaveMsg]](ms.get.utf8String, classOf[Array[LeaveMsg]])
					else Array[LeaveMsg]()

					if(leaveMsg != "") {
						val lmsg = LeaveMsg(leaveMsg, System.currentTimeMillis, 0)
						val newLms = Array(lmsg) ++ existMsg
						redis.hset(REDIS_KEY_GIFT_REPORT_LINE_MSG, id, mapper.writeValueAsString(newLms))
					}

					val result = s"""{
						"id":"${id}",
						"uid":"${jj.get("uid").asText}",
						"bank_name":"${jj.get("bank_name").asText}",
						"bank_code":"${jj.get("bank_code").asText}",
						"account":"${jj.get("account").asText}",
						"account_name":"${jj.get("account_name").asText}",
						"lname":"${jj.get("lname").asText}",
						"code":"${jj.get("code").asText}",
						"status":${status},
						"line_img":"${jj.get("line_img").asText}",
						"cts":${jj.get("cts").asLong},
						"mark":"${mark}"
					}"""

					redis.hset(REDIS_KEY_GIFT_REPORT_MONEY, id, result)				
				}
			}

			for {
				kk <- k1
				ss <- getMoney(searchName, searchStatus)				
			} yield { 
				Ok(views.html.admin_money_index(ss, searchName, searchStatus))
			}

		// }

	}


	def update(isLine : Boolean = true) = Action.async { request =>
		val reportTable = if(isLine) REDIS_KEY_GIFT_REPORT_LINE else REDIS_KEY_GIFT_REPORT_LINE_TOPIC

		val body = request.body.asFormUrlEncoded.get
		println("body:" + body)

		val id = body("id")(0)
		val status = 
			if(body("status").size > 0)
				body("status")(0).toInt
			else 1
		val mark = 
			if(body("mark").size > 0)
				body("mark")(0)
			else ""
		val leaveMsg = 
			if(body("magmsg").size > 0)
				body("magmsg")(0)
			else ""
		val searchLid = body.get("searchLid").getOrElse(List(""))(0)
		val searchStatus = body.get("searchStatus").getOrElse(List("1"))(0).toInt

		val k1 = for {
			rm <- redis.hget(reportTable, id)
			ms <- redis.hget(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
		} yield {
			if(rm.isDefined) {
				val jj = mapper.readTree(rm.get.utf8String)
				val existMsg = if(ms.isDefined)
					mapper.readValue[Array[LeaveMsg]](ms.get.utf8String, classOf[Array[LeaveMsg]])
				else Array[LeaveMsg]()

				if(leaveMsg != "") {
					val lmsg = LeaveMsg(leaveMsg, System.currentTimeMillis, 0)
					val newLms = Array(lmsg) ++ existMsg
					redis.hset(REDIS_KEY_GIFT_REPORT_LINE_MSG, id, mapper.writeValueAsString(newLms))
				}

				val result = s"""{
					"id":"${id}",
					"uid":"${jj.get("uid").asText}",
					"lid":"${jj.get("lid").asText}",
					"lnick":"${jj.get("lnick").asText}",
					"lname":"${jj.get("lname").asText}",
					"code":"${jj.get("code").asText}",
					"status":${status},
					"line_img":"${jj.get("line_img").asText}",
					"cts":${jj.get("cts").asLong},
					"mark":"${mark}"
				}"""

				redis.hset(reportTable, id, result)				
			}
		}
		for {
			kk <- k1
			ss <- if(isLine) getLines(searchLid, searchStatus, true) 
			else getLines(searchLid, searchStatus, false)			
		} yield { 
			if(isLine)
				Ok(views.html.admin_index(ss, searchLid, searchStatus))
			else 
				Ok(views.html.admin_index_topic(ss, searchLid, searchStatus))
		}
	}

	def updateSe() = Action.async { request =>
		val body = request.body.asFormUrlEncoded.get
		// body:Map(updatebt -> List(修改), id -> List(96ca3066-ed54-4a94-aecb-85b955edd6a1_50DE4E8F8F8476D79DA9C3CA264357F9B9B52E9F), status -> List(1), mark -> List(), magmsg -> List())
		println("body:" + body)

		val id = body("id")(0)
		val status = 
			if(body("status").size > 0)
				body("status")(0).toInt
			else 1
		val mark = 
			if(body("mark").size > 0)
				body("mark")(0)
			else ""
		val leaveMsg = 
			if(body("magmsg").size > 0)
				body("magmsg")(0)
			else ""
		val searchName = body.get("searchName").getOrElse(List(""))(0)
		val searchStatus = body.get("searchStatus").getOrElse(List("1"))(0).toInt

		// if(status == 5) {
		// 	for {
		// 		k1 <- redis.hdel(REDIS_KEY_GIFT_REPORT_SE, id)
		// 		k2 <- redis.hdel(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
		// 		ss <- getSe(searchName, searchStatus)				
		// 	} yield { 
		// 		Ok(views.html.admin_se_index(ss, searchName, searchStatus))
		// 	}
		// } else {
			val k1 = for {
				rm <- redis.hget(REDIS_KEY_GIFT_REPORT_SE, id)
				ms <- redis.hget(REDIS_KEY_GIFT_REPORT_LINE_MSG, id)
			} yield {
				if(rm.isDefined) {
					val jj = mapper.readTree(rm.get.utf8String)
					val existMsg = if(ms.isDefined)
						mapper.readValue[Array[LeaveMsg]](ms.get.utf8String, classOf[Array[LeaveMsg]])
					else Array[LeaveMsg]()

					if(leaveMsg != "") {
						val lmsg = LeaveMsg(leaveMsg, System.currentTimeMillis, 0)
						val newLms = Array(lmsg) ++ existMsg
						redis.hset(REDIS_KEY_GIFT_REPORT_LINE_MSG, id, mapper.writeValueAsString(newLms))
					}

					val result = s"""{
						"id":"${id}",
						"uid":"${jj.get("uid").asText}",
						"postcode":"${jj.get("postcode").asText}",
						"address":"${jj.get("address").asText}",
						"account":"${jj.get("account").asText}",
						"phone":"${jj.get("phone").asText}",
						"lname":"${jj.get("lname").asText}",
						"code":"${jj.get("code").asText}",
						"status":${status},
						"line_img":"${jj.get("line_img").asText}",
						"cts":${jj.get("cts").asLong},
						"mark":"${mark}"
					}"""

					println("mark:" + mark)
					redis.hset(REDIS_KEY_GIFT_REPORT_SE, id, result)				
				}
			}

			for {
				kk <- k1
				ss <- getSe(searchName, searchStatus)				
			} yield { 
				Ok(views.html.admin_se_index(ss, searchName, searchStatus))
			}

		}

	// }

}