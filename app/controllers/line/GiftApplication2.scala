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

import scala.util.parsing.json.JSONFormat
import scala.collection.mutable.ListBuffer
import controllers.line.Constants._
import controllers.line.TraceApplication._
import java.util.Random

object GiftApplication2 extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))
	val reportStartTime = 0l;

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)

	val NEXT_TIME = 4000000 * 1l
	val MAX_SHOW = 500
	// val MAX_HIT_COUNT = 10
	val CODE_COUNT = 3

	def getPlaySeNextTime(uid :String, lgid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_GIFT_SE_NEW_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current lgid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}

	def createSe() = Action { request =>
		Logger.info(s"createGift")
		val body = request.body
		val data = body.asText.getOrElse("")
		val ff = mapper.readTree(data)
		val id = java.util.UUID.randomUUID.toString
		// val result = s"""{
		// 	"id":"${id}",
		// 	"base_money":${ff.get("base_money").asInt},
		// 	"type":"FR_SE",
		// 	"name":"${ff.get("name").asText}",
		// 	"max_count":${ff.get("max_count").asInt},
		// 	"img":"${ff.get("img").asText}",
		// 	"position":${ff.get("position").asInt},
		// 	"ct":${System.currentTimeMillis},
		// 	"max_ncount":${2 * ff.get("max_count").asInt}
		// }"""

		val result = s"""{
			"id":"${id}",
			"base_money":${ff.get("base_money").asInt},
			"type":"FR_SE",
			"name":"${ff.get("name").asText}",
			"max_count":${ff.get("max_count").asInt},
			"img":"${ff.get("img").asText}",
			"position":${ff.get("position").asInt},
			"ct":${System.currentTimeMillis},
			"max_ncount":${ff.get("max_ncount").asInt},
			"point":${ff.get("point").asInt}
		}"""		
		redis.hset(REDIS_KEY_GIFT_SE, id, result)

		Ok(result)		
	}


	def getCode(lgid : String, uid : String) = {
		val oldRan = (new java.util.Random().nextInt(89) + 10).toString
		val ran = lgid.substring(0,CODE_COUNT) + lgid.substring(lgid.length-CODE_COUNT, lgid.length) + 
			uid.substring(0,CODE_COUNT) + uid.substring(uid.length-CODE_COUNT, uid.length) + oldRan
		(oldRan, ran)
	}


	def playNewSe(lgid : String, uid : String, lid : String) = Action.async {
		Logger.info(s"playLine current lgid:${lgid}, uid:${uid}, lid:${lid}")
		
		val now = System.currentTimeMillis
		for {
			kk <- redis.hget(REDIS_KEY_GIFT_SE, lgid)
			tt <- redis.hget(REDIS_KEY_GIFT_SE_NEW_CHECK_TIME, uid)
			dd <- redis.hget(REDIS_KEY_GIFT_SE_PLAY_COUNT, lgid + "_" + uid)
			// cc <- redis.scard(REDIS_KEY_GIFT_TASKS + uid)
		} yield {
			// if(cc == 0) {
			// 	Ok("errmsg:請先將此項目加入任務清單")
			// } else 
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time1 is not over,current lkid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok("err:3:" + sss)
			} else if(!kk.isDefined) {
				Ok("err:4")
			} else  {
				val jj = mapper.readTree(kk.get.utf8String)
				val sd = 
					if(dd.isDefined) {
						dd.get.utf8String.toInt + 1 
					} else {
						1
					}
				redis.hset(REDIS_KEY_GIFT_SE_CHECK_TIME + lgid, uid, now)
				redis.hset(REDIS_KEY_GIFT_SE_PLAY_COUNT, lgid + "_" + uid, sd.toString)
				val nextPlay = now + 3600000l * 2 + 60000l * new Random().nextInt(120)
				redis.hset(REDIS_KEY_GIFT_SE_NEW_CHECK_TIME, uid, nextPlay)


				if(sd >= jj.get("max_ncount").asInt) {
					val code = getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_SE_ACHIVE + uid, lgid)
					// redis.srem(REDIS_KEY_GIFT_TASKS + uid, lgid)

					Ok("achived:" + sd.toString)
				} else {
					Ok(sd.toString)
				}				
			}
		}
	}

	def playSe(lgid : String, uid : String, lid : String) = Action {
		Logger.info(s"playLine current lgid:${lgid}, uid:${uid}, lid:${lid}")
		Ok("err:4")
	}


	def getSeKeys(uid : String) = {
		redis.hkeys(REDIS_KEY_GIFT_SE).map { ss => // Future[Seq[String]]
			ss.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getRunningSeKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_SE).map { ss => // Future[Seq[String]]
			ss.filter{ kk => !achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getAchivedSeKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_SE).map { ss => // Future[Seq[String]]
			ss.filter{ kk => achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getSeCount(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 
			redis.hmget(REDIS_KEY_GIFT_SE_PLAY_COUNT, keys : _*).map { ss => // Future[Seq[Option[R]]]
				keys.zip(ss).map { k => if(k._2.isDefined) (k._1, k._2.get.utf8String) else (k._1, "0") }.toMap
			}
	}

	def getRandomNum(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 		
			redis.hmget(REDIS_KEY_GIFT_RANDOM_KEY, keys : _*).map { ss => // Future[Seq[Option[R]]]
				keys.zip(ss).map { k => if(k._2.isDefined) (k._1, k._2.get.utf8String) else (k._1, "") }.toMap
			}
	}

	def getCodeNum(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 		
			redis.hmget(REDIS_KEY_GIFT_CODE_KEY, keys : _*).map { ss => // Future[Seq[Option[R]]]
				keys.zip(ss).map { k => if(k._2.isDefined) (k._1, k._2.get.utf8String) else (k._1, "") }.toMap
			}
	}

	def getSePlayTime(keys : List[String]) = {
		Future.sequence(keys.map { key =>
			val lgid = key.split("_")(0)
			val uid = key.split("_")(1) 
			redis.hget(REDIS_KEY_GIFT_SE_CHECK_TIME + lgid, uid).map { kk => //Future[Option[R]]
				if(kk.isDefined) 
					(key, kk.get.utf8String)
				else 
					(key, "0")
			} 
		})
	}

	def getTaskSes(uid : String) = Action.async {
		for {
			s1 <- getSeKeys(uid)
			kCount <- getSeCount(s1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_SE) //Future[Map[String, R]]
		} yield {
			// val ee1 = all.filter { k =>
			// 		val llkey = k._1 + "_" + uid
			// 		val currentCount = kCount(llkey).toInt
			// 		val jj = mapper.readTree(k._2.utf8String)
			// 		val maxCount = jj.get("max_count").asInt
			// 		(currentCount < maxCount) && currentCount > 0
			// 	}.map { k =>
			// 		val llkey = k._1 + "_" + uid
			// 		val jj = mapper.readTree(k._2.utf8String)
			// 		val maxCount = jj.get("max_count").asInt
			// 		val currentCount = kCount(llkey).toInt
			// 		(jj.get("ct").asLong, s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
			// 	}.toArray

			val ee2 = all.filter { k =>
					val llkey = k._1 + "_" + uid
					val currentCount = kCount(llkey).toInt
					val jj = mapper.readTree(k._2.utf8String)
					val maxCount = jj.get("max_count").asInt					
					(currentCount < maxCount) && currentCount == 0
				}.map { k =>
					val llkey = k._1 + "_" + uid
					val jj = mapper.readTree(k._2.utf8String)
					val maxCount = jj.get("max_count").asInt
					val currentCount = kCount(llkey).toInt
					(new java.util.Random().nextInt(100), s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			// scala.util.Sorting.quickSort(ee1)
			scala.util.Sorting.quickSort(ee2)

			// val ee = ee1 ++ ee2.take(MAX_SHOW - ee1.length)
			val ee = ee2.reverse.take(MAX_SHOW)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}

	def getNewTaskSes(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_SE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getRunningSeKeys(uid, s1)
			kCount <- getSeCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_SE) //Future[Map[String, R]]
		} yield {
			val ee1 = all.filter { k =>
					val llkey = k._1 + "_" + uid
					kCount.getOrElse(llkey, "-1").toInt > 0
				}.map { k =>
					val llkey = k._1 + "_" + uid
					val jj = mapper.readTree(k._2.utf8String)
					val currentCount = kCount.getOrElse(llkey, "0").toInt
					(jj.get("ct").asLong, s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			val ee2 = all.filter { k =>
					val llkey = k._1 + "_" + uid
					kCount.getOrElse(llkey, "-1").toInt == 0
				}.map { k =>
					val llkey = k._1 + "_" + uid
					val jj = mapper.readTree(k._2.utf8String)
					val currentCount = kCount.getOrElse(llkey, "0").toInt
					(new java.util.Random().nextInt(100), s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			scala.util.Sorting.quickSort(ee1)
			scala.util.Sorting.quickSort(ee2)

			val ee = ee1 ++ ee2.reverse.take(MAX_SHOW - ee1.length)
			// val ee = ee2.reverse.take(MAX_SHOW)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}


	def getStatus(name : String, keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 
			redis.hmget(name, keys : _*).map { ss => // Future[Seq[Option[R]]]
				keys.zip(ss).map { k => 
					if(k._2.isDefined) {
						val jj = mapper.readTree(k._2.get.utf8String)
						(k._1, jj.get("status").asInt) 
					}
					else (k._1, 0) }.toMap
			}
	}
	def getLineMsgCount(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 
			redis.hmget(REDIS_KEY_GIFT_REPORT_LINE_MSG, keys : _*).map { ss => // Future[Seq[Option[R]]]
				keys.zip(ss).map { k => 
					if(k._2.isDefined) {
						println("aa:" + k._2.get.utf8String)
						val lms = mapper.readValue[Array[LeaveMsg]](k._2.get.utf8String, classOf[Array[LeaveMsg]])
						(k._1, lms.length) 
					}
					else (k._1, 0) }.toMap
			}
	}

	// -1 now show, 0: show
	def getAchived(uid : String) = Action.async {
		val lastDisplayTime = System.currentTimeMillis - (14 * 86400000l)

		val lineAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- GiftApplication.getAchivedLineKeys(uid, s1)
			kCount <- GiftApplication.getLineCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- GiftApplication.getLinePlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE) //Future[Map[String, R]]
			statuss <- getStatus(REDIS_KEY_GIFT_REPORT_LINE, v1)
			msgCount <- getLineMsgCount(v1)
		} yield {
			val tTime = v3.toMap

			s1.map { _.utf8String }.flatMap { archiveId =>
				if(all.contains(archiveId)) {
					val k = (archiveId, all(archiveId))
					val jj = mapper.readTree(k._2.utf8String)
					val llkey = k._1 + "_" + uid
					val lastPlayTime = tTime(llkey).toLong
					val code = k._1.substring(0,CODE_COUNT) + "_" +  uid.substring(0,CODE_COUNT) + ranCount(llkey)
					val newCode = codeCount(llkey)

					if(lastPlayTime < lastDisplayTime) None 
					else if(lastPlayTime < reportStartTime)
						Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}","status":-1}"""))
					else if(statuss.contains(llkey) && statuss(llkey) == 5) None 
					else {
						Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}","status":${statuss(llkey)},"leave_msg_count":${msgCount(llkey)} }"""))
					}
				} else None
			}.toArray
		}

		val moneyAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_MONEY_ACHIVE + uid) //Future[Seq[R]]			
			v1 <- GiftApplication.getAchivedMoneyKeys(uid,s1)
			kCount <- GiftApplication.getMoneyCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- GiftApplication.getMoneyPlayTime(v1)
			all <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
			statuss <- getStatus(REDIS_KEY_GIFT_REPORT_MONEY, v1)
			msgCount <- getLineMsgCount(v1)
		} yield {
			val tTime = v3.toMap
			s1.map { _.utf8String }.flatMap { archiveId =>
				if(all.contains(archiveId)) {
					val k = (archiveId, all(archiveId))
					val jj = mapper.readTree(k._2.utf8String)
					val llkey = k._1 + "_" + uid
					val currentCount = kCount(llkey).toInt	
					val lastPlayTime = tTime(llkey).toLong
					val code = k._1.substring(0,3) + "_" +  uid.substring(0,3) + ranCount(llkey)	
					val newCode = codeCount(llkey)

					if(lastPlayTime < lastDisplayTime) None 
					else if(lastPlayTime < reportStartTime) 
						Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${currentCount},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}"}"""))
					else if(statuss.contains(llkey) && statuss(llkey) == 5) None 
					else 
						Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${currentCount},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}","status":${statuss(llkey)},"leave_msg_count":${msgCount(llkey)} }"""))					
				} else None
			}.toArray
		}

		val bagAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_BAG_ACHIVE + uid) //Future[Seq[R]]
			v1 <- GiftApplication1.getAchivedBagKeys(uid, s1)
			kCount <- GiftApplication1.getBagCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- GiftApplication1.getBagPlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_BAG) //Future[Map[String, R]]
			statuss <- getStatus(REDIS_KEY_GIFT_REPORT_BAG, v1)
			msgCount <- getLineMsgCount(v1)
		} yield {
			val tTime = v3.toMap

			s1.map { _.utf8String }.flatMap { archiveId =>
				if(all.contains(archiveId)) {
					val k = (archiveId, all(archiveId))
					val jj = mapper.readTree(k._2.utf8String)
					val llkey = k._1 + "_" + uid
					val lastPlayTime = tTime(llkey).toLong
					val code = k._1.substring(0,CODE_COUNT) + "_" +  uid.substring(0,CODE_COUNT) + ranCount(llkey)
					val newCode = codeCount(llkey)


					if(lastPlayTime < lastDisplayTime) None 
					else if(lastPlayTime < reportStartTime) 
					Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}"}"""))
					else if(statuss.contains(llkey) && statuss(llkey) == 5) None 
					else 
					Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}","status":${statuss(llkey)},"leave_msg_count":${msgCount(llkey)} }"""))
				} else None
			}.toArray
		}

		val seAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_SE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getAchivedSeKeys(uid, s1)
			kCount <- getSeCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- getSePlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_SE) //Future[Map[String, R]]
			statuss <- getStatus(REDIS_KEY_GIFT_REPORT_SE, v1)
			msgCount <- getLineMsgCount(v1)
		} yield {
			val tTime = v3.toMap

			s1.map { _.utf8String }.flatMap { archiveId =>
				if(all.contains(archiveId)) {
					val k = (archiveId, all(archiveId))
					val jj = mapper.readTree(k._2.utf8String)
					val llkey = k._1 + "_" + uid
					val lastPlayTime = tTime(llkey).toLong
					val code = k._1.substring(0,CODE_COUNT) + "_" +  uid.substring(0,CODE_COUNT) + ranCount(llkey)
					val newCode = codeCount(llkey)

					if(lastPlayTime < lastDisplayTime) None  
					else if(lastPlayTime < reportStartTime)
					Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}"}"""))
					else if(statuss.contains(llkey) && statuss(llkey) == 5) None 
					else
					Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}","status":${statuss(llkey)},"leave_msg_count":${msgCount(llkey)} }"""))
				} else None
			}.toArray

		}

		for{
			aa <- lineAchived
			bb <- moneyAchived
			cc <- bagAchived
			dd <- seAchived
		} yield {
			val ee = aa ++ bb ++ cc ++ dd
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)				
		}
	}


	def getAllPlayer() = Action.async {
		for {
			dd <- redis.hgetall(REDIS_KEY_GIFT_LINE_PLAY_COUNT) //Future[Map[String, R]]
			kk <- redis.hgetall(REDIS_KEY_GIFT_MONEY_PLAY_COUNT)
			vv <- redis.hgetall(REDIS_KEY_GIFT_BAG_PLAY_COUNT)
			vv1 <- redis.hgetall(REDIS_KEY_GIFT_SE_PLAY_COUNT)
			randomCount <- redis.hgetall(REDIS_KEY_GIFT_RANDOM_KEY)
			newRandomCount <- redis.hgetall(REDIS_KEY_GIFT_CODE_KEY)
			ff <- redis.hgetall(LuckyApplication.REDIS_KEY_LUCKY_RUNNING)
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
			allBag <- redis.hgetall(REDIS_KEY_GIFT_BAG)
			allSe <- redis.hgetall(REDIS_KEY_GIFT_SE)
		} yield {

			var total = 0
			Logger.info(s"line size ${dd.size}")
			val d1 = dd.toList.flatMap { ka => 
				total = total + ka._2.utf8String.toInt
				val gid = ka._1.split("_")(0)
				if(allLine.contains(gid)) {
					val jj = mapper.readTree(allLine(gid).utf8String)
					if(ka._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((ka._2.utf8String.toInt, ka._1, jj.get("name").asText))
					else None
				} else None
			}.toArray

			Logger.info(s"money size ${kk.size}")
			val d2 = kk.toList.flatMap { kb => 
				total = total + kb._2.utf8String.toInt
				val gid = kb._1.split("_")(0)
				if(allMoney.contains(gid)) {
					val jj = mapper.readTree(allMoney(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			Logger.info(s"bag size ${vv.size}")
			val d3 = vv.toList.flatMap { kb => 
				total = total + kb._2.utf8String.toInt
				val gid = kb._1.split("_")(0)
				if(allBag.contains(gid)) {
					val jj = mapper.readTree(allBag(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			Logger.info(s"se size ${vv.size}")
			val d4 = vv1.toList.flatMap { kb => 
				total = total + kb._2.utf8String.toInt
				val gid = kb._1.split("_")(0)
				if(allSe.contains(gid)) {
					val jj = mapper.readTree(allSe(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			var luckyCount = 0
			Logger.info(s"lucky size ${ff.size}")
			ff.toList.foreach { k => 
				val jj = mapper.readTree(k._2.utf8String)
				luckyCount = luckyCount + jj.get("current_count").asInt
			}

			val ee = (d1 ++ d2 ++ d3 ++ d4)
			scala.util.Sorting.quickSort(ee)
			
			val ooo = ee.reverse.map { k =>
				val gid = k._2.split("_")(0) 
				val uid = k._2.split("_")(1)
				val ranNum = if(randomCount.contains(k._2)) randomCount(k._2).utf8String else ""
				val code = gid.substring(0,CODE_COUNT) + uid.substring(0,CODE_COUNT) + ranNum 
				val newCode = if(newRandomCount.contains(k._2)) newRandomCount(k._2).utf8String else ""

				s"""{"count":"${k._1}","code":"${code}","uid":"${uid}","gid":"${gid}", "name":"${k._3}","new_code":"${newCode}" } """ }.mkString("[",",","]")
			val resp = s"""{"gift_count":${total}, "lucky_count":${luckyCount}, "gift_user":${ooo}}"""
			Ok(resp)							
		}
	}

	def getSinglePlayer(sid : String) = Action.async {
		val s1 = for {
			dd <- redis.hgetall(REDIS_KEY_GIFT_LINE_PLAY_COUNT) //Future[Map[String, R]]
			kk <- redis.hgetall(REDIS_KEY_GIFT_MONEY_PLAY_COUNT)
			vv <- redis.hgetall(REDIS_KEY_GIFT_BAG_PLAY_COUNT)
			vv1 <- redis.hgetall(REDIS_KEY_GIFT_SE_PLAY_COUNT)
			randomCount <- redis.hgetall(REDIS_KEY_GIFT_RANDOM_KEY)
			newRandomCount <- redis.hgetall(REDIS_KEY_GIFT_CODE_KEY)			
			ff <- redis.hgetall(LuckyApplication.REDIS_KEY_LUCKY_RUNNING)
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
			allBag <- redis.hgetall(REDIS_KEY_GIFT_BAG)
			allSe <- redis.hgetall(REDIS_KEY_GIFT_SE)
		} yield {

			var total = 0
			Logger.info(s"line size ${dd.size}")
			val d1 = dd.toList.flatMap { ka => 
				total = total + ka._2.utf8String.toInt
				val gid = ka._1.split("_")(0)

				if(allLine.contains(gid)) {
					val jj = mapper.readTree(allLine(gid).utf8String)
					if(ka._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((ka._2.utf8String.toInt, ka._1, jj.get("name").asText))
					else None
				} else None
			}.toArray

			Logger.info(s"money size ${kk.size}")
			val d2 = kk.toList.flatMap { kb => 
				total = total + kb._2.utf8String.toInt
				val gid = kb._1.split("_")(0)
				if(allMoney.contains(gid)) {
					val jj = mapper.readTree(allMoney(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			Logger.info(s"bag size ${vv.size}")
			val d3 = vv.toList.flatMap { kb => 
				total = total + kb._2.utf8String.toInt
				val gid = kb._1.split("_")(0)
				if(allBag.contains(gid)) {
					val jj = mapper.readTree(allBag(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			Logger.info(s"se size ${vv.size}")
			val d4 = vv1.toList.flatMap { kb => 
				total = total + kb._2.utf8String.toInt
				val gid = kb._1.split("_")(0)
				if(allSe.contains(gid)) {
					val jj = mapper.readTree(allSe(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			var luckyCount = 0
			Logger.info(s"lucky size ${ff.size}")
			ff.toList.foreach { k => 
				val jj = mapper.readTree(k._2.utf8String)
				luckyCount = luckyCount + jj.get("current_count").asInt
			}

			val ee = (d1 ++ d2 ++ d3 ++ d4)
			scala.util.Sorting.quickSort(ee)
			
			val gg = ee.reverse.flatMap { k =>
					val gid = k._2.split("_")(0) 
					val uid = k._2.split("_")(1).substring(0,CODE_COUNT)
					val newUid = k._2.split("_")(1).substring(0, CODE_COUNT) + 
							k._2.split("_")(1).substring(k._2.split("_")(1).length - CODE_COUNT, k._2.split("_")(1).length)

					val ranNum = if(randomCount.contains(k._2)) randomCount(k._2).utf8String else ""
					val code = gid.substring(0,CODE_COUNT) + uid.substring(0,CODE_COUNT) + ranNum
					// println("newRandomCount size:" + newRandomCount.size + "k._2:" + k._2 + "," + newRandomCount.contains(k._2))
					val newCode = if(newRandomCount.contains(k._2)) newRandomCount(k._2).utf8String else ""
					if(sid.length != 3 && sid.length != 6 ) 
						None
					else if(sid.length == 3 && sid != uid)
						None
					else if(sid.length == 6 && sid != newUid)
						None
					else 
						Some((k._2,k._3, code, newCode))
			}
			gg
		}

		for{
			v1 <- s1
			v2 <- GiftApplication.getLinePlayTime(v1.map{ e => e._1 }.toList)
			v3 <- GiftApplication.getMoneyPlayTime(v1.map{ e => e._1 }.toList)
			v4 <- GiftApplication1.getBagPlayTime(v1.map{ e => e._1 }.toList)
			v5 <- getSePlayTime(v1.map{ e => e._1 }.toList)
		} yield {

			val lineTime = v2.toMap
			val moneyTime = v3.toMap
			val bagTime = v4.toMap
			val seTime = v5.toMap
			val see = v1.map { vv =>
				val ftime = if(lineTime.contains(vv._1) && lineTime(vv._1).toLong != 0) {
					lineTime(vv._1).toLong
				} else if(moneyTime.contains(vv._1) && moneyTime(vv._1).toLong != 0) { 
					moneyTime(vv._1).toLong 
				} else if(bagTime.contains(vv._1) && bagTime(vv._1).toLong != 0) { 
					bagTime(vv._1).toLong 
				} else if(seTime.contains(vv._1) && seTime(vv._1).toLong != 0) { 
					seTime(vv._1).toLong 
				} else {
					0l
				}
				(ftime, s"""${vv._2}#${vv._4}#${vv._3}""")
				// (ftime, s"""${vv._2}#${vv._4}""")
			}.toArray
			scala.util.Sorting.quickSort(see)
			Ok(see.reverse.map { sss => sss._2}.mkString("\n"))
		}
	}	


	def getProduct() = Action.async {
		for {
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
			allBag <- redis.hgetall(REDIS_KEY_GIFT_BAG) //Future[Map[String, R]]
			allSe <- redis.hgetall(REDIS_KEY_GIFT_SE) //Future[Map[String, R]]
		} yield {
			val aa = allLine.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("id").asText.substring(0,3),jj.get("max_count").asInt, jj.get("name").asText)
			}
			val bb = allMoney.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("id").asText.substring(0,3),jj.get("max_count").asInt, jj.get("name").asText)
			}
			val dd = allBag.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("id").asText.substring(0,3),jj.get("max_count").asInt, jj.get("name").asText)
			}
			val ee = allSe.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("id").asText.substring(0,3),jj.get("max_count").asInt, jj.get("name").asText)
			}			
			val cc = aa ++ bb ++ dd	++ ee	
			Ok(cc.map { c => c._1 + "#" + c._3 + "#" + c._2}.mkString("\n"))
		}
	}

	def createNewProduct() { 
		List(REDIS_KEY_GIFT_LINE, REDIS_KEY_GIFT_MONEY, REDIS_KEY_GIFT_BAG).map { redisName =>
			redis.hgetall(redisName).map { ll => //Future[Map[String, R]]
				ll.foreach { k =>
					val jj = mapper.readTree(k._2.utf8String)
					val po = 50 * jj.get("max_count").asInt

					val result = s"""{
						"id":"${jj.get("id").asText}",
						"type":"${jj.get("type").asText}",
						"name":"${jj.get("name").asText}",
						"max_count":${jj.get("max_count").asInt},
						"img":"${jj.get("img").asText}",
						"position":${jj.get("position").asInt},
						"ct":${jj.get("ct").asLong},
						"max_ncount":${jj.get("max_ncount").asInt},
						"point":${po}
					}"""
					redis.hset(redisName, k._1, result)
				}
			}
		}

		redis.hgetall(REDIS_KEY_GIFT_SE).map { ll => //Future[Map[String, R]]
			ll.foreach { k =>
				val jj = mapper.readTree(k._2.utf8String)
				val po = 50 * jj.get("max_count").asInt

				val result = s"""{
					"id":"${jj.get("id").asText}",
					"base_money":${jj.get("base_money").asInt},
					"type":"${jj.get("type").asText}",
					"name":"${jj.get("name").asText}",
					"max_count":${jj.get("max_count").asInt},
					"img":"${jj.get("img").asText}",
					"position":${jj.get("position").asInt},
					"ct":${jj.get("ct").asLong},
					"max_ncount":${jj.get("max_ncount").asInt},
					"point":${po}
				}"""
				redis.hset(REDIS_KEY_GIFT_SE, k._1, result)
			}
		}
	}



}