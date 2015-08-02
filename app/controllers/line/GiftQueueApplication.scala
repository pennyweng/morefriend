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


object GiftQueueApplication extends Controller {
	implicit val system = Akka.system

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val redis = GiftApplication.redis

	val LINE_BASE = 50
	val MONEY_BASE = 100
	val BAG_BASE = 100
	val PRODUCT_BASE = 500
	val MAX_TASK = 4

	def getMyHqueue(uid : String) = {
		redis.hgetall(REDIS_KEY_GIFT_HQUEUE + uid).map { hg => //Future[Map[String, R]]
			hg.map { hh => //lgid + "_" + giftType
				val lgid = hh._1.substring(0, hh._1.indexOf("_"))
				val giftType = hh._1.substring(hh._1.indexOf("_") + 1)
				val numm = hh._2.utf8String.toLong
				(giftType, lgid, numm)
			}.groupBy{e => e._1}
		} // Map[String,scala.collection.immutable.Iterable[(String, String, Long)]]
	}

	def getMyQueue(uid : String) = Action.async {
		Logger.info(s"getMyQueue uid:${uid}")
		val cc = for {
			v1 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_LINE") //Future[Option[R]]
			v2 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_MONY")
			v3 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_BAG")
			v4 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_SE")
		} yield {
			val vv1 = if(v1.isDefined) v1.get.utf8String.toLong else 1l
			val vv2 = if(v2.isDefined) v2.get.utf8String.toLong else 1l
			val vv3 = if(v3.isDefined) v3.get.utf8String.toLong else 1l
			val vv4 = if(v4.isDefined) v4.get.utf8String.toLong else 1l
			Map("FR_LINE" -> vv1, "FR_MONY" -> vv2, "FR_BAG" -> vv3, "FR_SE" -> vv4)
		}

		for {
			kk <- getMyHqueue(uid)
			vv <- getProductDetail(kk) //Iterable[(String, Seq[(Option[akka.util.ByteString], Long)])]
			cc1 <- cc //Map[String,Long]
		} yield {

			val result = vv.map { ss =>
				val ccount = cc1(ss._1)
				ss._2.flatMap { k =>
					if(k._1.isDefined) {
						Some(s"""{"ll":${k._1.get.utf8String},"cn":${k._2}, "sc":${ccount}}""")
					} else None
				}
			}.flatten.mkString("[",",","]")
			Ok(result)

			// val result = vv.flatten.flatMap { k =>
			// 	if(k._1.isDefined) {
			// 		Some(s"""{"ll":${k._1.get.utf8String},"cn":${k._2}}""")
			// 	} else None
			// }.mkString("[",",","]")
			// Ok(result)
		}
	}

	def getDetail(giftType : String) = {
		if(giftType == "FR_LINE") REDIS_KEY_GIFT_LINE
		else if(giftType == "FR_MONY") REDIS_KEY_GIFT_MONEY
		else if(giftType == "FR_BAG") REDIS_KEY_GIFT_BAG
		else REDIS_KEY_GIFT_SE
	}

	def getProductDetail(dd : scala.collection.immutable.Map[String,scala.collection.immutable.Iterable[(String, String, Long)]]) = {
		Future.sequence(dd.map { ss =>
			val redisName = getDetail(ss._1)
			val lgids = ss._2.map { e => e._2 }.toList
			val ccount = ss._2.map { e => e._3 }.toList

			redis.hmget(redisName, lgids : _*).map { r =>//Future[Seq[Option[R]]]
				(ss._1, r zip ccount)
			} 
		})
	}

	def addInQueue(uid : String, lgid : String, giftType : String) = Action.async {
		Logger.info(s"addInQueue:lgid ${lgid}, uid:${uid}, giftType:${giftType}")

		val giftDetail = 
			if(giftType == "FR_LINE") (REDIS_KEY_GIFT_LINE, LINE_BASE)
			else if(giftType == "FR_MONY") (REDIS_KEY_GIFT_MONEY, MONEY_BASE)
			else if(giftType == "FR_BAG") (REDIS_KEY_GIFT_BAG, BAG_BASE)
			else (REDIS_KEY_GIFT_SE, PRODUCT_BASE)

		val baseIncCount = 
			for {
				gl <- redis.hget(giftDetail._1, lgid)
			} yield {
				if(gl.isDefined) {
					val ss = mapper.readTree(gl.get.utf8String)
					val inc = {
						val v = ss.get("max_count").asInt / giftDetail._2
						if(v == 0) 1 else v
					}
					Some(inc)
				} else None 
			}
			
		val incrName = REDIS_KEY_GIFT_INCRBY + giftType 
		val queueName = REDIS_KEY_GIFT_ZQUEUE + giftType
		val key = lgid + "_" + uid
		for {
			kk <- redis.hlen(REDIS_KEY_GIFT_HQUEUE + uid)
			s <- redis.zscore(queueName, key) //  Future[Option[Double]]
			bi <- baseIncCount
			d <- getNum(s, bi, incrName)
		} yield {
			if (kk > MAX_TASK) Ok("err:1")
			else if(d == -1) Ok("err:2")
			else {
				redis.zadd(queueName, (d.toDouble, key))
				redis.hset(REDIS_KEY_GIFT_HQUEUE + uid, lgid + "_" + giftType, d)

				Ok(d+"")
			}
		}
	}

	def getNum(s : Option[Double], bi : Option[Int], incrName : String) = {
		if(!s.isDefined && bi.isDefined) { 
			if(bi.get == 1) {
				redis.incrby(incrName, bi.get)
			} else {
				val sk = 1 + (bi.get - 1) * 100 + new java.util.Random().nextInt(20)
				Future.successful(sk.toLong)
			}
		} else Future.successful(-1l)	
	}

	def removeFromQueue(uid : String, lgid : String, giftType : String) =  Action.async {
		val key = lgid + "_" + uid
		for {
			r1 <- redis.zrem(REDIS_KEY_GIFT_ZQUEUE + giftType, key)
			r2 <- redis.hdel(REDIS_KEY_GIFT_HQUEUE + uid, lgid + "_" + giftType)
		} yield {
			Ok("")
		}
	}	


	def goToday(uid : String) = Action.async {
		redis.zadd(REDIS_KEY_GIFT_TQUEUE, (System.currentTimeMillis, uid)).map { s =>
			Ok("")
		}
	}
	
 //Future[Option[R]]		
	def getGiftCount(giftType : String) = Action.async {
		redis.get(REDIS_KEY_GIFT_NOW_SEND +  giftType).map { k =>
			val rt = if(k.isDefined) k.get.utf8String else "1"
			Ok(rt)
		}
	}

	def converQueue() {
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
			// Logger.info(s"line size ${dd.size}")
			// val lline = dd.toList.flatMap { ka => 
			// 	total = total + ka._2.utf8String.toInt
			// 	val gid = ka._1.split("_")(0)
			// 	val uid = ka._1.split("_")(1)
			// 	if(allLine.contains(gid)) {
			// 		val jj = mapper.readTree(allLine(gid).utf8String)
			// 		val currentCount = ka._2.utf8String.toInt
			// 		val pert =currentCount * 100 / jj.get("max_count").asInt
			// 		if((currentCount < jj.get("max_count").asInt & pert > 80) ) {
			// 			println("pert:" + pert + ",currentCount:" + currentCount + ",name:" + jj.get("name").asText + ",id:" + ka._1)
			// 			Some(pert, uid, gid, "FR_LINE")
			// 		} else None
			// 	} else None
			// }.toArray

			// scala.util.Sorting.quickSort(lline)
			// lline.reverse.foreach { e =>
			// 	addInQueue1(e._2, e._3, e._4)
			// }


			// Logger.info(s"money size ${kk.size}")
			// val llmoney = kk.toList.flatMap { ka => 
			// 	total = total + ka._2.utf8String.toInt
			// 	val gid = ka._1.split("_")(0)
			// 	val uid = ka._1.split("_")(1)
			// 	if(allMoney.contains(gid)) {
			// 		val jj = mapper.readTree(allMoney(gid).utf8String)
			// 		val currentCount = ka._2.utf8String.toInt
			// 		val pert =currentCount * 100 / jj.get("max_count").asInt
			// 		if((currentCount < jj.get("max_count").asInt & pert > 70) ) {
			// 			Some(pert, uid, gid, "FR_MONY")
			// 		} else None
			// 	} else None
			// }.toArray


			// scala.util.Sorting.quickSort(llmoney)
			// llmoney.reverse.foreach { e =>
			// 	addInQueue1(e._2, e._3, e._4)
			// 	// println(e)
			// }


			Logger.info(s"bag size ${vv.size}")
			val bagg = vv.toList.flatMap { ka => 
				total = total + ka._2.utf8String.toInt
				val gid = ka._1.split("_")(0)
				val uid = ka._1.split("_")(1)
				if(allBag.contains(gid)) {
					val jj = mapper.readTree(allBag(gid).utf8String)
					val currentCount = ka._2.utf8String.toInt
					val pert =currentCount * 100 / jj.get("max_count").asInt
					if((currentCount < jj.get("max_count").asInt & pert > 70) ) {
						Some(pert, uid, gid, "FR_BAG")
					} else None
				} else None
			}.toArray

			scala.util.Sorting.quickSort(bagg)
			bagg.reverse.foreach { e =>
				addInQueue1(e._2, e._3, e._4)
				// println(e)
			}			

		}
	}	


	def addInQueue1(uid : String, lgid : String, giftType : String) {
		Logger.info(s"addInQueue:lgid ${lgid}, uid:${uid}, giftType:${giftType}")

		val giftDetail = 
			if(giftType == "FR_LINE") (REDIS_KEY_GIFT_LINE, LINE_BASE)
			else if(giftType == "FR_MONY") (REDIS_KEY_GIFT_MONEY, MONEY_BASE)
			else if(giftType == "FR_BAG") (REDIS_KEY_GIFT_BAG, BAG_BASE)
			else (REDIS_KEY_GIFT_SE, PRODUCT_BASE)

		val baseIncCount = 
			for {
				gl <- redis.hget(giftDetail._1, lgid)
			} yield {
				if(gl.isDefined) {
					val ss = mapper.readTree(gl.get.utf8String)
					val inc = {
						val v = ss.get("max_count").asInt / giftDetail._2
						if(v == 0) 1 else v
					}
					Some(inc)
				} else None 
			}
			
		val incrName = REDIS_KEY_GIFT_INCRBY + giftType 
		val queueName = REDIS_KEY_GIFT_ZQUEUE + giftType
		val key = lgid + "_" + uid
		for {
			kk <- redis.hlen(REDIS_KEY_GIFT_HQUEUE + uid)
			s <- redis.zscore(queueName, key) //  Future[Option[Double]]
			bi <- baseIncCount
			d <- getNum(s, bi, incrName)
		} yield {
			if(d == -1) Ok("err:2")
			else {
				redis.zadd(queueName, (d.toDouble, key))
				redis.hset(REDIS_KEY_GIFT_HQUEUE + uid, lgid + "_" + giftType, d)
			}
		}
	}

}
