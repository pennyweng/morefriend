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

case class QClass(lgid : String, name : String, uid : String, ncount : Long )

object AdminQueue extends Controller {
	implicit val system = Akka.system

	val mapper = new ObjectMapper()
	val redis = GiftApplication.redis
	val CODE_COUNT = 3

	def setSendCount(giftType : String, newCount : Long) = Action.async {
		val key = REDIS_KEY_GIFT_NOW_SEND + giftType
		redis.set(key, newCount).map { ss =>
			Ok("")
		}
	}

	def getAllCurrentCounts() = {
		for {
			v1 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_LINE") //Future[Option[R]]
			v2 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_MONY")
			v3 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_BAG")
			v4 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + "FR_SE")
		} yield {
			val vv1 = if(v1.isDefined) v1.get.utf8String.toLong else 1l
			val vv2 = if(v2.isDefined) v2.get.utf8String.toLong else 1l
			val vv3 = if(v3.isDefined) v3.get.utf8String.toLong else 1l
			val vv4 = if(v4.isDefined) v4.get.utf8String.toLong else 1l
			(vv1, vv2, vv3, vv4)
		}
	}

	def getAllCurrentCount() = Action.async {
		getAllCurrentCounts().map { vv =>
			Ok(s"""{"line_count": ${vv._1}, "money_count":${vv._2}, "bag_count":${vv._3}, "se_count":${vv._4}}""")
		}
	}


	
	
	def getQueues(giftType : String) = Action.async {
		ggQueues(giftType).map { rs =>
			Ok(views.html.admin_queue_list(rs._1, rs._2))
		}
	}


	def ggQueues(giftType : String) = {
		val key = REDIS_KEY_GIFT_NOW_SEND + giftType

		val ss = for {
			vv <- redis.get(key)
			kk <- if(vv.isDefined) redis.zrangebyscoreWithscores(REDIS_KEY_GIFT_ZQUEUE + giftType, Limit(vv.get.utf8String.toLong + 1), Limit(vv.get.utf8String.toLong + 1000)) else 
			redis.zrangebyscoreWithscores(REDIS_KEY_GIFT_ZQUEUE + giftType, Limit(0.toLong), Limit(1000.toLong))
		} yield { //Seq[(R, Double)
			kk.map { r =>
				(r._1.utf8String, r._2)
			}
		}

		for {
			vv <- ss
			pd <- getProductDetails(giftType, vv)
		} yield {
			val res = pd.flatMap { pp => // (Option[akka.util.ByteString], (String, Double))
				if(pp._1.isDefined) {
					// val key = lgid + "_" + uid
					val lgid = pp._2._1.split("_")(0)
					val uid = pp._2._1.split("_")(1)
					val tt = mapper.readTree(pp._1.get.utf8String)
					Some(QClass(lgid, tt.get("name").asText, uid, pp._2._2.toLong))
				} else None	
			}//.mkString("[",",","]")
			(res, giftType)
		}
	}

	def getCode(lgid : String, uid : String) = {
		val oldRan = (new java.util.Random().nextInt(89) + 10).toString
		val ran = lgid.substring(0,CODE_COUNT) + lgid.substring(lgid.length-CODE_COUNT, lgid.length) + 
			uid.substring(0,CODE_COUNT) + uid.substring(uid.length-CODE_COUNT, uid.length) + oldRan
		(oldRan, ran)
	}


	def approve(giftType : String, to : String) = Action.async {
		println("giftType:" + giftType + ",to:" + to)

		val queueName = REDIS_KEY_GIFT_ZQUEUE + giftType
		val now = System.currentTimeMillis

		val f1 = for {
			v1 <- redis.get(REDIS_KEY_GIFT_NOW_SEND + giftType)
			v2 <- {
					val s = if(v1.isDefined) v1.get.utf8String.toLong else 0l
					println("start:" + s + ",to:" + to)
					redis.zrangebyscore(queueName, Limit(s.toDouble + 1), Limit(to.toDouble)) 
				} //Future[Seq[R]]
		} yield {
			v2.map { e => 
				val key = e.utf8String 
				val lgid = key.split("_")(0)
				val uid = key.split("_")(1)
				println("approve lgid:" + lgid + ",uid:" + uid)
				val code = getCode(lgid, uid)
				redis.hset(getCheckTime(giftType) + lgid, uid, now)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(getArchive(giftType) +uid, lgid)
				redis.hdel(REDIS_KEY_GIFT_HQUEUE + uid, lgid + "_" + giftType)
			}
			redis.set(REDIS_KEY_GIFT_NOW_SEND + giftType, to)
		}

		for {
			ff1 <- f1
			rs <- ggQueues(giftType)
		} yield {
			Ok(views.html.admin_queue_list(rs._1, rs._2))
		}

	}

	def getProductDetails( giftType : String, ss : Seq[(String, Double)]) = {
		println("getProductDetails:" + ss.size)
		// val key = lgid + "_" + uid
		val kk = ss.map { e => e._1.split("_")(0) }.toList
		if(kk.size > 0) {
			redis.hmget(getDetail(giftType), kk : _*).map { r =>
				r zip ss
			}
		} else Future.successful(Seq())
	}

	def getDetail(giftType : String) = {
		if(giftType == "FR_LINE") REDIS_KEY_GIFT_LINE
		else if(giftType == "FR_MONY") REDIS_KEY_GIFT_MONEY
		else if(giftType == "FR_BAG") REDIS_KEY_GIFT_BAG
		else REDIS_KEY_GIFT_SE
	}	

	def getArchive(giftType : String) = {
		if(giftType == "FR_LINE") REDIS_KEY_GIFT_LINE_ACHIVE
		else if(giftType == "FR_MONY") REDIS_KEY_GIFT_MONEY_ACHIVE
		else if(giftType == "FR_BAG") REDIS_KEY_GIFT_BAG_ACHIVE
		else REDIS_KEY_GIFT_SE_ACHIVE
	}

	def getCheckTime(giftType : String) = {
		if(giftType == "FR_LINE") REDIS_KEY_GIFT_LINE_CHECK_TIME
		else if(giftType == "FR_MONY") REDIS_KEY_GIFT_MONEY_CHECK_TIME
		else if(giftType == "FR_BAG") REDIS_KEY_GIFT_BAG_CHECK_TIME
		else REDIS_KEY_GIFT_SE_CHECK_TIME
	}

	def delay() {
		val end = System.currentTimeMillis - (5 * 86400000)
		println("run delay " + end)
		redis.zrangebyscore(REDIS_KEY_GIFT_TQUEUE, Limit(0.toDouble), Limit(end.toDouble)).map { s =>
			s.map { s1 =>
				println("del queue" + s1.utf8String)
			}
		}

		for {
			k1 <- redis.keys("GHQ*") //Future[Seq[String]]
			k2 <- redis.zrange(REDIS_KEY_GIFT_TQUEUE, 0l, -1l)	//Future[Seq[R]]
		} {
			val ss0 = k1.map { _.substring(3) }
			val ss = k2.map { _.utf8String }
			val ss2 = ss0 diff ss
			ss2.foreach { ss22 =>
				println("del delay ss2:" + ss22)
			}
		}
		

	}

}