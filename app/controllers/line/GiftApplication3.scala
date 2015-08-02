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

object GiftApplication3 extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NEXT_TIME = 3600000 * 1l
	val NEXT_B_TIME = 3800000 * 1l	
	val MAX_SHOW = 300
	val MAX_SHOW_LINE_PRODUCT = 100
	val MAX_HIT_COUNT = 10
	val CODE_COUNT = 3
	val reportStartTime = 0l;

	def getPlayLineTopicNextTime(uid :String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		redis.hget(REDIS_KEY_GIFT_LINE_TOPIC_NEW_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current lgid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}

	def getPlayLineProductNextTime(uid :String, lgid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_GIFT_LINE_PRODUCT_NEW_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current lgid:${lgid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createLineTopic() = Action { request =>
		Logger.info(s"createLine createGift")
		val body = request.body
		val data = body.asText.getOrElse("")
		val ff = mapper.readTree(data)
		val id = java.util.UUID.randomUUID.toString

		val result = s"""{
			"id":"${id}",
			"type":"FR_L_TOPIC",
			"name":"${ff.get("name").asText}",
			"max_count":${ff.get("max_count").asInt},
			"img":"${ff.get("img").asText}",
			"position":${ff.get("position").asInt},
			"ct":${System.currentTimeMillis},
			"max_ncount":${ff.get("max_ncount").asInt},
			"point":${ff.get("point").asInt}
		}"""

		redis.hset(REDIS_KEY_GIFT_LINE_TOPIC, id, result)

		Ok(result)		
	}

	def createLineProduct() = Action { request =>
		Logger.info(s"createLineProduct")
		val body = request.body
		val data = body.asText.getOrElse("")
		val ff = mapper.readTree(data)
		val id = java.util.UUID.randomUUID.toString

		val result = s"""{
			"id":"${id}",
			"type":"FR_L_PRODUCT",			
			"name":"${ff.get("name").asText}",
			"max_count":${ff.get("max_count").asInt},
			"img":"${ff.get("img").asText}",
			"position":${ff.get("position").asInt},
			"ct":${System.currentTimeMillis},
			"max_ncount":${ff.get("max_ncount").asInt},
			"point":${ff.get("point").asInt}
		}"""

		redis.hset(REDIS_KEY_GIFT_LINE_PRODUCT, id, result)

		Ok(result)		
	}
	

	def getCode(lgid : String, uid : String) = {
		val oldRan = (new java.util.Random().nextInt(89) + 10).toString
		val ran = lgid.substring(0,CODE_COUNT) + lgid.substring(lgid.length-CODE_COUNT, lgid.length) + 
			uid.substring(0,CODE_COUNT) + uid.substring(uid.length-CODE_COUNT, uid.length) + oldRan
		(oldRan, ran)
	}

	def playNewLineTopic(lgid : String, uid : String, lid : String) = Action.async {
		Logger.info(s"playNewLineTopic current lgid:${lgid}, uid:${uid}, lid:${lid}")
		
		val now = System.currentTimeMillis
		for {
			kk <- redis.hget(REDIS_KEY_GIFT_LINE_TOPIC, lgid)
			tt <- redis.hget(REDIS_KEY_GIFT_LINE_TOPIC_NEW_CHECK_TIME, uid)
			dd <- redis.hget(REDIS_KEY_GIFT_LINE_TOPIC_PLAY_COUNT, lgid + "_" + uid)
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
				redis.hset(REDIS_KEY_GIFT_LINE_TOPIC_CHECK_TIME + lgid, uid, now)
				redis.hset(REDIS_KEY_GIFT_LINE_TOPIC_PLAY_COUNT, lgid + "_" + uid, sd.toString)
				val nextPlay = now + 3600000l * 1 + 60000l * new Random().nextInt(60)
				redis.hset(REDIS_KEY_GIFT_LINE_TOPIC_NEW_CHECK_TIME, uid, nextPlay)

				if(sd >= jj.get("max_ncount").asInt) {
					val code = getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_LINE_TOPIC_ACHIVE+uid, lgid)
					// redis.srem(REDIS_KEY_GIFT_TASKS + uid, lgid)

					Ok("achived:" + sd.toString)
				} else {
					Ok(sd.toString)
				}				
			}
		}
	}


	def playNewLineProduct(lgid : String, uid : String, lid : String) = Action.async {
		Logger.info(s"playMoney current lgid:${lgid}, uid:${uid}, lid:${lid}")
		
		val now = System.currentTimeMillis
		for {
			kk <- redis.hget(REDIS_KEY_GIFT_LINE_PRODUCT, lgid)
			tt <- redis.hget(REDIS_KEY_GIFT_LINE_PRODUCT_NEW_CHECK_TIME, uid)
			dd <- redis.hget(REDIS_KEY_GIFT_LINE_PRODUCT_PLAY_COUNT, lgid + "_" + uid)
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
				redis.hset(REDIS_KEY_GIFT_LINE_PRODUCT_PLAY_COUNT, lgid + "_" + uid, sd.toString)
				redis.hset(REDIS_KEY_GIFT_LINE_PRODUCT_CHECK_TIME + lgid, uid, now)
				val nextPlay = now + 3600000l * 2 + 60000l * new Random().nextInt(120)
				redis.hset(REDIS_KEY_GIFT_LINE_PRODUCT_NEW_CHECK_TIME, uid, nextPlay)

				if(sd >= jj.get("max_ncount").asInt) {
					val code = getCode(lgid, uid)

					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)	
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_LINE_PRODUCT_ACHIVE+uid, lgid)
					// redis.srem(REDIS_KEY_GIFT_TASKS + uid, lgid)
					Ok("achived:" + sd.toString)
				} else {
					Ok(sd.toString)
				}
			}
		}
	}	


	def getAchivedLineTopicKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_LINE_TOPIC).map { ss => // Future[Seq[String]]
			ss.filter{ kk => achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getAchivedLineProductKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_LINE_PRODUCT).map { ss => // Future[Seq[String]]
			ss.filter{ kk => achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getRunningLineKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_LINE_TOPIC).map { ss => // Future[Seq[String]]
			ss.filter{ kk => !achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getLineTopicCount(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 
			redis.hmget(REDIS_KEY_GIFT_LINE_TOPIC_PLAY_COUNT, keys : _*).map { ss => // Future[Seq[Option[R]]]
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
	

	def getRunningLineProductKeys(uid : String, achivedIds : Seq[akka.util.ByteString]) = {
		val achived = achivedIds.map { _.utf8String }.toList
		redis.hkeys(REDIS_KEY_GIFT_LINE_PRODUCT).map { ss => // Future[Seq[String]]
			ss.filter{ kk => !achived.contains(kk) }.map { ee =>
				ee + "_" + uid
			}.toList
		}
	}

	def getLineProductCount(keys : List[String]) = {
		if(keys == null || keys.length == 0) 
			Future.successful(Map[String, String]())
		else 		
		redis.hmget(REDIS_KEY_GIFT_LINE_PRODUCT_PLAY_COUNT, keys : _*).map { ss => // Future[Seq[Option[R]]]
			keys.zip(ss).map { k => if(k._2.isDefined) (k._1, k._2.get.utf8String) else (k._1, "0") }.toMap
		}
	}

	def getNewTaskLineTopic(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_TOPIC_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getRunningLineKeys(uid, s1)
			kCount <- getLineTopicCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE_TOPIC) //Future[Map[String, R]]
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

			println("ee1:" + ee1.size + ",ee2:" + ee2.size)
			val ee = ee1 ++ ee2.reverse.take(MAX_SHOW - ee1.length)
			// val ee = ee2.reverse.take(MAX_SHOW)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}

	def getNewTaskLineProduct(uid : String) = Action.async {
		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_PRODUCT_ACHIVE + uid) //Future[Seq[R]]			
			v1 <- getRunningLineProductKeys(uid,s1)
			s2 <- getLineProductCount(v1) // List[(String, String)]
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE_PRODUCT) //Future[Map[String, R]]
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

			val ee = ee1 ++ ee2.reverse.take(MAX_SHOW_LINE_PRODUCT - ee1.length)
			// val ee = ee2.take(MAX_SHOW_LINE_PRODUCT)
			val ooo = ee.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)	
		}
	}

	def getLineTopicPlayTime(keys : List[String]) = {
		Future.sequence(keys.map { key =>
			val lgid = key.split("_")(0)
			val uid = key.split("_")(1) 
			redis.hget(REDIS_KEY_GIFT_LINE_TOPIC_CHECK_TIME + lgid, uid).map { kk => //Future[Option[R]]
				if(kk.isDefined) 
					(key, kk.get.utf8String)
				else 
					(key, "0")
			} 
		})
	}


	def getLineProductPlayTime(keys : List[String]) = {
		Future.sequence(keys.map { key =>
			val lgid = key.split("_")(0)
			val uid = key.split("_")(1) 
			redis.hget(REDIS_KEY_GIFT_LINE_PRODUCT_CHECK_TIME + lgid, uid).map { kk => //Future[Option[R]]
				if(kk.isDefined) 
					(key, kk.get.utf8String)
				else 
					(key, "0")
			} 
		})
	}

	def getAchived(uid : String) = Action.async {
		println("getAchived:")
		val lastDisplayTime = System.currentTimeMillis - (14 * 86400000l)

		val lineAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
			v1 <- GiftApplication.getAchivedLineKeys(uid, s1)
			kCount <- GiftApplication.getLineCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- GiftApplication.getLinePlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE) //Future[Map[String, R]]
			statuss <- GiftApplication2.getStatus(REDIS_KEY_GIFT_REPORT_LINE, v1)
			msgCount <- GiftApplication2.getLineMsgCount(v1)
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

		val lineTopicAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_TOPIC_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getAchivedLineTopicKeys(uid, s1)
			kCount <- getLineTopicCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- getLineTopicPlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE_TOPIC) //Future[Map[String, R]]
			statuss <- GiftApplication2.getStatus(REDIS_KEY_GIFT_REPORT_LINE_TOPIC, v1)
			msgCount <- GiftApplication2.getLineMsgCount(v1)
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
		
		val lineProductAchived = for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_PRODUCT_ACHIVE + uid) //Future[Seq[R]]
			v1 <- getAchivedLineProductKeys(uid, s1)
			kCount <- getLineProductCount(v1) // List[(String, String)]
			ranCount <- getRandomNum(v1)
			codeCount <- getCodeNum(v1)
			v3 <- getLineProductPlayTime(v1)		
			all <- redis.hgetall(REDIS_KEY_GIFT_LINE_PRODUCT) //Future[Map[String, R]]
			statuss <- GiftApplication2.getStatus(REDIS_KEY_GIFT_REPORT_LINE_PRODUCT, v1)
			msgCount <- GiftApplication2.getLineMsgCount(v1)
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

		for{
			aa <- lineAchived
			bb <- lineTopicAchived
			cc <- lineProductAchived
		} yield {
			val ee = aa ++ bb ++ cc
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)				
		}
	}


}
