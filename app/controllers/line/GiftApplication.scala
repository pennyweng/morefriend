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

object GiftApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NEXT_TIME = 3600000 * 1l
	val NEXT_B_TIME = 3800000 * 1l	
	val MAX_SHOW = 400
	val MAX_SHOW_MONEY = 100
	val MAX_HIT_COUNT = 10
	val CODE_COUNT = 3

	def getPlayLineNextTime(uid :String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		redis.hget(REDIS_KEY_GIFT_LINE_NEW_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current lgid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}

	def getPlayMoneyNextTime(uid :String, lgid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_GIFT_MONEY_NEW_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current lgid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createLine() = Action { request =>
		Logger.info(s"createGift")
		val body = request.body
		val data = body.asText.getOrElse("")
		val ff = mapper.readTree(data)
		val id = java.util.UUID.randomUUID.toString
		// val result = s"""{
		// 	"id":"${id}",
		// 	"type":"FR_LINE",
		// 	"name":"${ff.get("name").asText}",
		// 	"max_count":${ff.get("max_count").asInt},
		// 	"img":"${ff.get("img").asText}",
		// 	"position":${ff.get("position").asInt},
		// 	"ct":${System.currentTimeMillis},
		// 	"max_ncount":${2 * ff.get("max_count").asInt}
		// }"""

		val result = s"""{
			"id":"${id}",
			"type":"FR_LINE",
			"name":"${ff.get("name").asText}",
			"max_count":${ff.get("max_count").asInt},
			"img":"${ff.get("img").asText}",
			"position":${ff.get("position").asInt},
			"ct":${System.currentTimeMillis},
			"max_ncount":${ff.get("max_ncount").asInt},
			"point":${ff.get("point").asInt}
		}"""

		redis.hset(REDIS_KEY_GIFT_LINE, id, result)

		Ok(result)		
	}

	def createMoney() = Action { request =>
		Logger.info(s"createMoney")
		val body = request.body
		val data = body.asText.getOrElse("")
		val ff = mapper.readTree(data)
		val id = java.util.UUID.randomUUID.toString
		
		// val result = s"""{
		// 	"id":"${id}",
		// 	"type":"FR_MONY",			
		// 	"name":"${ff.get("name").asText}",
		// 	"max_count":${ff.get("max_count").asInt},
		// 	"img":"${ff.get("img").asText}",
		// 	"position":${ff.get("position").asInt},
		// 	"ct":${System.currentTimeMillis},
		// 	"max_ncount":${2 * ff.get("max_count").asInt}
		// }"""

		val result = s"""{
			"id":"${id}",
			"type":"FR_MONY",			
			"name":"${ff.get("name").asText}",
			"max_count":${ff.get("max_count").asInt},
			"img":"${ff.get("img").asText}",
			"position":${ff.get("position").asInt},
			"ct":${System.currentTimeMillis},
			"max_ncount":${ff.get("max_ncount").asInt},
			"point":${ff.get("point").asInt}
		}"""

		redis.hset(REDIS_KEY_GIFT_MONEY, id, result)

		Ok(result)		
	}
	

	def getCode(lgid : String, uid : String) = {
		val oldRan = (new java.util.Random().nextInt(89) + 10).toString
		val ran = lgid.substring(0,CODE_COUNT) + lgid.substring(lgid.length-CODE_COUNT, lgid.length) + 
			uid.substring(0,CODE_COUNT) + uid.substring(uid.length-CODE_COUNT, uid.length) + oldRan
		(oldRan, ran)
	}

	def playNewLine(lgid : String, uid : String, lid : String) = Action.async {
		Logger.info(s"playLine current lgid:${lgid}, uid:${uid}, lid:${lid}")
		
		val now = System.currentTimeMillis
		for {
			kk <- redis.hget(REDIS_KEY_GIFT_LINE, lgid)
			tt <- redis.hget(REDIS_KEY_GIFT_LINE_NEW_CHECK_TIME, uid)
			dd <- redis.hget(REDIS_KEY_GIFT_LINE_PLAY_COUNT, lgid + "_" + uid)
			// cc <- redis.scard(REDIS_KEY_GIFT_TASKS + uid)
		} yield {
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time1 is not over,current lkid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong -now
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
				redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
				redis.hset(REDIS_KEY_GIFT_LINE_PLAY_COUNT, lgid + "_" + uid, sd.toString)
				val nextPlay = now + 3600000l * 1 + 60000l * new Random().nextInt(60)
				redis.hset(REDIS_KEY_GIFT_LINE_NEW_CHECK_TIME, uid, nextPlay)

				if(sd >= jj.get("max_ncount").asInt) {
					val code = getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
					// redis.srem(REDIS_KEY_GIFT_TASKS + uid, lgid)

					Ok("achived:" + sd.toString)
				} else {
					Ok(sd.toString)
				}				
			}
		}
	}

	def updateWinner() {
		val lgid = "134e7089-9c3b-4c0e-8547-b9c5b60c9248"
		val uid = "073F6845E532FA21E1A964491D4CB2C83DCDED06"

		val code = getCode(lgid, uid)
		redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
		redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
		redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
	}

	def playLine(lgid : String, uid : String, lid : String) = Action {
		Logger.info(s"playLine current lgid:${lgid}, uid:${uid}, lid:${lid}")
		Ok("err:4")
	}

	def playNewMoney(lgid : String, uid : String, lid : String) = Action.async {
		Logger.info(s"playMoney current lgid:${lgid}, uid:${uid}, lid:${lid}")
		
		val now = System.currentTimeMillis
		for {
			kk <- redis.hget(REDIS_KEY_GIFT_MONEY, lgid)
			tt <- redis.hget(REDIS_KEY_GIFT_MONEY_NEW_CHECK_TIME, uid)
			dd <- redis.hget(REDIS_KEY_GIFT_MONEY_PLAY_COUNT, lgid + "_" + uid)
			// cc <- redis.scard(REDIS_KEY_GIFT_TASKS + uid)
		} yield {
			// if(cc == 0) {
			// 	Ok("errmsg:請先將此項目加入任務清單")
			// } else 
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current lkid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok("err:3:" + sss)
			} else  {
				val jj = mapper.readTree(kk.get.utf8String)
				val sd = if(dd.isDefined) dd.get.utf8String.toInt + 1 else 1
				redis.hset(REDIS_KEY_GIFT_MONEY_PLAY_COUNT, lgid + "_" + uid, sd.toString)
				redis.hset(REDIS_KEY_GIFT_MONEY_CHECK_TIME + lgid, uid, now)
				val nextPlay = now + 3600000l * 2 + 60000l * new Random().nextInt(120)
				redis.hset(REDIS_KEY_GIFT_MONEY_NEW_CHECK_TIME, uid, nextPlay)

				if(sd >= jj.get("max_ncount").asInt) {
					val code = getCode(lgid, uid)

					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)	
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_MONEY_ACHIVE+uid, lgid)
					// redis.srem(REDIS_KEY_GIFT_TASKS + uid, lgid)
					Ok("achived:" + sd.toString)
				} else {
					Ok(sd.toString)
				}
			}
		}
	}	

	def playMoney(lgid : String, uid : String, lid : String) = Action {
		Logger.info(s"playMoney current lgid:${lgid}, uid:${uid}, lid:${lid}")
		Ok("err:4")
	}	

	def getLineKeys(uid : String) = {
		redis.hkeys(REDIS_KEY_GIFT_LINE).map { ss => // Future[Seq[String]]
			ss.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getAchivedLineKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_LINE).map { ss => // Future[Seq[String]]
			ss.filter{ kk => achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getRunningLineKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_LINE).map { ss => // Future[Seq[String]]
			ss.filter{ kk => !achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getLineCount(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 
			redis.hmget(REDIS_KEY_GIFT_LINE_PLAY_COUNT, keys : _*).map { ss => // Future[Seq[Option[R]]]
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

	def getLinePlayTime(keys : List[String]) = {
		Future.sequence(keys.map { key =>
			val lgid = key.split("_")(0)
			val uid = key.split("_")(1) 
			redis.hget(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid).map { kk => //Future[Option[R]]
				if(kk.isDefined) 
					(key, kk.get.utf8String)
				else 
					(key, "0")
			} 
		})
	}

	def getTaskLines(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getRunningLineKeys(uid, s1)
			kCount <- getLineCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE) //Future[Map[String, R]]
		} yield {
			// val kCount = s2.toMap
			
			val ee2 = all.filter { k =>
					val llkey = k._1 + "_" + uid
					kCount.getOrElse(llkey, "-1").toInt == 0
				}.map { k =>
					val llkey = k._1 + "_" + uid
					val jj = mapper.readTree(k._2.utf8String)
					val maxCount = jj.get("max_count").asInt
					val currentCount = kCount.getOrElse(llkey, "0").toInt
					(jj.get("ct").asLong, s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			// scala.util.Sorting.quickSort(ee1)
			scala.util.Sorting.quickSort(ee2)

			// val ee = ee1 ++ ee2.reverse.take(MAX_SHOW - ee1.length)
			val ee = ee2.reverse.take(MAX_SHOW)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}


	def getMoneyKeys(uid : String) = {
		redis.hkeys(REDIS_KEY_GIFT_MONEY).map { ss => // Future[Seq[String]]
			ss.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getAchivedMoneyKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_MONEY).map { ss => // Future[Seq[String]]
			ss.filter{ kk => achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}	

	def getRunningMoneyKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_MONEY).map { ss => // Future[Seq[String]]
			ss.filter{ kk => !achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getMoneyCount(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 		
		redis.hmget(REDIS_KEY_GIFT_MONEY_PLAY_COUNT, keys : _*).map { ss => // Future[Seq[Option[R]]]
			keys.zip(ss).map { k => if(k._2.isDefined) (k._1, k._2.get.utf8String) else (k._1, "0") }.toMap
		}
	}

	//redis.hget(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid)
	def getMoneyPlayTime(keys : List[String]) = {
		Future.sequence(keys.map { key =>
			val lgid = key.split("_")(0)
			val uid = key.split("_")(1) 
			redis.hget(REDIS_KEY_GIFT_MONEY_CHECK_TIME + lgid, uid).map { kk => //Future[Option[R]]
				if(kk.isDefined) 
					(key, kk.get.utf8String)
				else 
					(key, "0")
			} 
		})
	}


	def getAchived(uid : String) = Action.async {
		val lastDisplayTime = System.currentTimeMillis - (15 * 86400000l)

		val lineAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getAchivedLineKeys(uid, s1)
			kCount <- getLineCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- getLinePlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE) //Future[Map[String, R]]
		} yield {
			val tTime = v3.toMap

			val achived = s1.map { _.utf8String }.toList
			all.filter { k => achived.contains(k._1) }.flatMap { k =>
				val jj = mapper.readTree(k._2.utf8String)
				val llkey = k._1 + "_" + uid
				val lastPlayTime = tTime(llkey).toLong
				val code = k._1.substring(0,CODE_COUNT) + "_" +  uid.substring(0,CODE_COUNT) + ranCount(llkey)
				val newCode = codeCount(llkey)
				Logger.info("lastPlayTime:" + lastPlayTime + ",lastDisplayTime:" + lastDisplayTime)
				if(lastPlayTime < lastDisplayTime) None else 
				Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}"}"""))
			}.toArray
		}

		val moneyAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_MONEY_ACHIVE + uid) //Future[Seq[R]]			
			v1 <- getAchivedMoneyKeys(uid,s1)
			kCount <- getMoneyCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- getMoneyPlayTime(v1)
			all <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
		} yield {
			val tTime = v3.toMap
			val achived = s1.map { _.utf8String }.toList

			all.filter { k => 
					achived.contains(k._1) }
				.flatMap { k =>
					val jj = mapper.readTree(k._2.utf8String)
					val llkey = k._1 + "_" + uid
					val currentCount = kCount(llkey).toInt	
					val lastPlayTime = tTime(llkey).toLong
					val code = k._1.substring(0,3) + "_" +  uid.substring(0,3) + ranCount(llkey)	
					val newCode = codeCount(llkey)

					if(lastPlayTime < lastDisplayTime) None else 
					Some((lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${currentCount},"lt":${lastPlayTime},"code":"${code}", "new_code":"${newCode}"}"""))
				}.toArray
		}

		for{
			aa <- lineAchived
			bb <- moneyAchived
		} yield {
			val ee = aa ++ bb
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)				
		}
	}



	def getTaskMoney(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_MONEY_ACHIVE + uid) //Future[Seq[R]]			
			v1 <- getRunningMoneyKeys(uid,s1)
			s2 <- getMoneyCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
		} yield {
			val kCount = s2.toMap
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
					kCount.getOrElse(llkey, "-1").toInt == 0
				}.map { k =>
					val llkey = k._1 + "_" + uid
					val jj = mapper.readTree(k._2.utf8String)
					val maxCount = jj.get("max_count").asInt
					val currentCount = kCount(llkey).toInt
					(new java.util.Random().nextInt(100), s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			// scala.util.Sorting.quickSort(ee1)
			scala.util.Sorting.quickSort(ee2)

			// val ee = ee1 ++ ee2.take(MAX_SHOW_MONEY - ee1.length)
			val ee = ee2.take(MAX_SHOW_MONEY)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}


	def getAllPlayer() = Action.async {
		for {
			dd <- redis.hgetall(REDIS_KEY_GIFT_LINE_PLAY_COUNT) //Future[Map[String, R]]
			kk <- redis.hgetall(REDIS_KEY_GIFT_MONEY_PLAY_COUNT)
			randomCount <- redis.hgetall(REDIS_KEY_GIFT_RANDOM_KEY)
			newRandomCount <- redis.hgetall(REDIS_KEY_GIFT_CODE_KEY)
			ff <- redis.hgetall(LuckyApplication.REDIS_KEY_LUCKY_RUNNING)
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
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

			var luckyCount = 0
			Logger.info(s"lucky size ${ff.size}")
			ff.toList.foreach { k => 
				val jj = mapper.readTree(k._2.utf8String)
				luckyCount = luckyCount + jj.get("current_count").asInt
			}

			val ee = (d1 ++ d2)
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
			randomCount <- redis.hgetall(REDIS_KEY_GIFT_RANDOM_KEY)
			newRandomCount <- redis.hgetall(REDIS_KEY_GIFT_CODE_KEY)			
			ff <- redis.hgetall(LuckyApplication.REDIS_KEY_LUCKY_RUNNING)
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
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

			var luckyCount = 0
			Logger.info(s"lucky size ${ff.size}")
			ff.toList.foreach { k => 
				val jj = mapper.readTree(k._2.utf8String)
				luckyCount = luckyCount + jj.get("current_count").asInt
			}

			val ee = (d1 ++ d2)
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
			v2 <- getLinePlayTime(v1.map{ e => e._1 }.toList)
			v3 <- getMoneyPlayTime(v1.map{ e => e._1 }.toList)
		} yield {

			val lineTime = v2.toMap
			val moneyTime = v3.toMap
			val see = v1.map { vv =>
				val ftime = if(lineTime.contains(vv._1) && lineTime(vv._1).toLong != 0) {
					lineTime(vv._1).toLong
				} else if(moneyTime.contains(vv._1) && moneyTime(vv._1).toLong != 0) { 
					moneyTime(vv._1).toLong 
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
		} yield {
			val aa = allLine.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("id").asText.substring(0,3),jj.get("max_count").asInt, jj.get("name").asText)
			}
			val bb = allMoney.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("id").asText.substring(0,3),jj.get("max_count").asInt, jj.get("name").asText)
			}
			val cc = aa ++ bb			
			Ok(cc.map { c => c._1 + "#" + c._3 + "#" + c._2}.mkString("\n"))
		}
	}

	def getProduct1() = Action.async {
		val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")

		for {
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
		} yield {
			val cc = allLine.map { ss =>
				val jj = mapper.readTree(ss._2.utf8String)
				(jj.get("ct").asLong, jj.get("img").asText,jj.get("name").asText)
			}.toArray


			// val bb = allMoney.map { ss =>
			// 	val jj = mapper.readTree(ss._2.utf8String)
			// 	(jj.get("ct").asLong, jj.get("img").asText,jj.get("name").asText)
			// }
			// val cc = aa ++ bb
			scala.util.Sorting.quickSort(cc)
			Ok(cc.reverse.map { c => s"""<img src="${c._2}" height="80" width="80" /><strong>${c._3}</strong> ${sdf.format(new java.util.Date(c._1))}"""}.mkString("""<br \>"""))
		}
	}

	def genNewCode() {
		redis.hgetall(REDIS_KEY_GIFT_RANDOM_KEY).map { kl =>
			kl.foreach { k =>
				val lgid = k._1.split("_")(0) 
				val uid = k._1.split("_")(1)
				val oldRan = k._2.utf8String
				val ran = lgid.substring(0,CODE_COUNT) + lgid.substring(lgid.length-CODE_COUNT, lgid.length) + 
				uid.substring(0,CODE_COUNT) + uid.substring(uid.length-CODE_COUNT, uid.length) + oldRan
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, k._1 , ran)
			}
		}
	}	


	def genEmptyCode() {
		for {
			dd <- redis.hgetall(REDIS_KEY_GIFT_LINE_PLAY_COUNT) //Future[Map[String, R]]
			kk <- redis.hgetall(REDIS_KEY_GIFT_MONEY_PLAY_COUNT)
			randomCount <- redis.hgetall(REDIS_KEY_GIFT_RANDOM_KEY)
			newRandomCount <- redis.hgetall(REDIS_KEY_GIFT_CODE_KEY)
			allLine <- redis.hgetall(REDIS_KEY_GIFT_LINE)
			allMoney <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
		} yield {
			Logger.info(s"line size ${dd.size}")
			val d1 = dd.toList.flatMap { ka => 
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
				val gid = kb._1.split("_")(0)
				if(allMoney.contains(gid)) {
					val jj = mapper.readTree(allMoney(gid).utf8String)
					if(kb._2.utf8String.toInt >= jj.get("max_count").asInt)
						Some((kb._2.utf8String.toInt, kb._1, jj.get("name").asText))				
					else None
				} else None
			}.toArray

			val ee = (d1 ++ d2)
			ee.map { k =>
				val lgid = k._2.split("_")(0) 
				val uid = k._2.split("_")(1)
				if(!randomCount.contains(k._2) && !newRandomCount.contains(k._2)) {
					val code = getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				}
			}
		}
	}

	def getNewTaskLines(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getRunningLineKeys(uid, s1)
			kCount <- getLineCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE) //Future[Map[String, R]]
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
					val currentCount = kCount(llkey).toInt
					(jj.get("ct").asLong, s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			scala.util.Sorting.quickSort(ee1)
			scala.util.Sorting.quickSort(ee2)

			Logger.info("ee1:" + ee1.size + ",ee2:" + ee2.size)
			val ee = ee1 ++ ee2.reverse.take(MAX_SHOW - ee1.length)
			// val ee = ee2.reverse.take(MAX_SHOW)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}

	def getNewTaskMoney(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_MONEY_ACHIVE + uid) //Future[Seq[R]]			
			v1 <- getRunningMoneyKeys(uid,s1)
			s2 <- getMoneyCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_MONEY) //Future[Map[String, R]]
		} yield {
			val kCount = s2.toMap
			val ee1 = all.filter { k =>
					val llkey = k._1 + "_" + uid
					kCount.getOrElse(llkey, "-1").toInt > 0
				}.map { k =>
					val llkey = k._1 + "_" + uid
					val jj = mapper.readTree(k._2.utf8String)
					val currentCount = kCount.getOrElse(llkey, "0").toInt
					(jj.get("ct").asLong, s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
				}.toArray

			// val ee2 = all.filter { k =>
			// 		val llkey = k._1 + "_" + uid
			// 		kCount.getOrElse(llkey, "-1").toInt == 0
			// 	}.map { k =>
			// 		val llkey = k._1 + "_" + uid
			// 		val jj = mapper.readTree(k._2.utf8String)
			// 		val currentCount = kCount.getOrElse(llkey, "0").toInt
			// 		(new java.util.Random().nextInt(100), s"""{"ll":${k._2.utf8String},"cc":${currentCount}}""")
			// 	}.toArray

			scala.util.Sorting.quickSort(ee1)
			// scala.util.Sorting.quickSort(ee2)

			val ee = ee1// ++ ee2.reverse.take(MAX_SHOW_MONEY - ee1.length)
			// val ee = ee2.take(MAX_SHOW_MONEY)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}


	// def requestLine(uid : String, gid : String, lid: String, lname:String) = {
	// 	val finishKey = uid + "_" + gid

	// 	val lineAchived = for {
	// 		s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
	// 		kCount <- getLineCount(ListfinishKey)) // List[(String, String)]
	// 		ranCount <- getRandomNum(List(finishKey)) 	
	// 		v3 <- getLinePlayTime(List(finishKey))		
	// 		kk <- redis.hget(REDIS_KEY_GIFT_LINE, gid) //Future[Map[String, R]]
	// 	} yield {
	// 		val tTime = v3.toMap
	// 		val achived = s1.map { _.utf8String }.toList
	// 		if(!achived.contains(finishKey)) {
	// 			Ok("err:0")
	// 		} else if(!kk.isDefined) {
	// 			Ok("err:1")
	// 		} else {
	// 			val jj = mapper.readTree(kk.get.utf8String)				
				
	// 		}



	// 		all.filter { k => achived.contains(k._1) }.map { k =>
	// 			val jj = mapper.readTree(k._2.utf8String)
	// 			val llkey = k._1 + "_" + uid
	// 			val lastPlayTime = tTime(llkey).toLong	
	// 			val code = k._1.substring(0,3) + "_" +  uid.substring(0,3) + ranCount(llkey)

	// 			(lastPlayTime, s"""{"ll":${k._2.utf8String},"cc":${kCount(llkey).toInt},"lt":${lastPlayTime},"code":"${code}"}""")
	// 		}.toArray
	// 	}

	// }

}
