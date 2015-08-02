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
import controllers.line.Constants._
import controllers.line.TraceApplication._

object PostApplication  extends Controller {
	implicit val system = Akka.system
	val redis = RedisClient(REDIS_HOST, 6379)
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NUM_OF_PAGE = 50
	val postPath = "./post"	

	val REDIS_KEY_REPORT_INTEREST = "RPT:"
	val REDIS_KEY_REPORT_PLACE = "RPP:"
	val REDIS_KEY_REPORT_CAREER = "RPC:"
	val REDIS_KEY_REPORT_OLD = "RPO:"
	val REDIS_KEY_REPORT_CONSTELLATION = "RPCL:"
	val REDIS_KEY_REPORT_MOTION = "RPM:"

	val REDIS_KEY_REPORT_ALL = "RPALL:"

	val REDIS_KEY_ONLINE = "ONL"

	val REDIS_KEY_POST = "POST"
	val REDIS_KEY_REPLY_POST = "RPOST"
	val REDIS_KEY_REPLY_POST_ID = "RPOSTD:"


	case class PostItem @JsonCreator() (
		@scala.beans.BeanProperty @JsonProperty("uid") uid : String, 
		@scala.beans.BeanProperty @JsonProperty("lid") lid : String,
		@scala.beans.BeanProperty @JsonProperty("data") data : String,
		@scala.beans.BeanProperty @JsonProperty("pid") pid : String,
		@scala.beans.BeanProperty @JsonProperty("ts") ts : Long,		
		@scala.beans.BeanProperty @JsonProperty("with_pic") with_pic : Boolean,
		@scala.beans.BeanProperty @JsonProperty("reply_count") reply_count : Int
	)

	def createPost(uid : String, lid : String, data : String,  categoryType : String, categoryId : String, withPic : Boolean) = Action(parse.temporaryFile) { request =>
		Logger.info( s"createPost uid:${uid}, lid:${lid}, data:${data}, categoryType:${categoryType}, categoryId:${categoryId}, withPic:${withPic}")
		if(BlockApplication.isBlock(uid, lid)) {
			Logger.info("blocker uid " + uid + " want to post")
			Status(509)("error")  
		} else {
			val pid = java.util.UUID.randomUUID.toString

			if(withPic) {
				val path = postPath + "/" + pid + ".jpg"
				Logger.info(" file upload path " + path)
		  		request.body.moveTo(new java.io.File(path), true)
		  	}

		  	val now = System.currentTimeMillis

			if(categoryType == "0") {
				redis.zadd(REDIS_KEY_REPORT_INTEREST + categoryId, (now, pid))			
			} else if(categoryType == "1") {
				redis.zadd(REDIS_KEY_REPORT_PLACE + categoryId, (now, pid))	
			} else if(categoryType == "2") {
				redis.zadd(REDIS_KEY_REPORT_CAREER + categoryId, (now, pid))	
			} else if(categoryType == "3") {
				redis.zadd(REDIS_KEY_REPORT_OLD + categoryId, (now, pid))	
			} else if(categoryType == "4") {
				redis.zadd(REDIS_KEY_REPORT_CONSTELLATION + categoryId, (now, pid))	
			} else if(categoryType == "5") {
				redis.zadd(REDIS_KEY_REPORT_MOTION + categoryId, (now, pid))	
			} else {
				for {
					aa <- redis.zadd(REDIS_KEY_REPORT + categoryId, (now, pid))
					bb <- redis.zcard(REDIS_KEY_REPORT + categoryId)
					kk <- redis.hget(REDIS_KEY_DISCUSS_ROOM, categoryId)
				} {
					kk.map { menu =>
						val jj = mapper.readTree(menu.utf8String)
						val newdata = s"""{"id":"${jj.get("id").asText}", "name":"${jj.get("name").asText}", 
						"desc":"${JSONFormat.quoteString(data).take(10) + "..." }", "num":"${bb}"} """						
						redis.hset(REDIS_KEY_DISCUSS_ROOM, jj.get("id").asText, newdata)
					}
				}
			}
			
			redis.zadd(REDIS_KEY_REPORT_ALL, (now, pid))

			val postData = s"""{"uid":"${uid}", "lid":"${JSONFormat.quoteString(lid)}", "data":"${JSONFormat.quoteString(data)}", "pid":"${pid}", "ts":${now}, "with_pic":${withPic}, "reply_count":0}"""
			redis.hset(REDIS_KEY_POST, pid, postData)
			updateOnline(uid)
			updateActive(uid)

		  	Ok("")
	  	}
	}

	def delPost(uid : String, categoryType : String, categoryId : String, pid : String) = Action {
		Logger.info( s"del post uid:${uid}, categoryType:${categoryType}, categoryId:${categoryId}, pid:${pid}")
		controllers.line.TraceApplication.updateOnline(uid)

		if(categoryType == "0") {
			if(categoryId == "") {
				ALL_INTERESTS_ID.foreach { id => 
					redis.zrem(REDIS_KEY_REPORT_INTEREST + id, pid)
				}
			} else 
				redis.zrem(REDIS_KEY_REPORT_INTEREST + categoryId, pid)			
		} else if(categoryType == "1") {
			redis.zrem(REDIS_KEY_REPORT_PLACE + categoryId, pid)	
		} else if(categoryType == "2") {
			redis.zrem(REDIS_KEY_REPORT_CAREER + categoryId, pid)	
		} else if(categoryType == "3") {
			redis.zrem(REDIS_KEY_REPORT_OLD + categoryId, pid)	
		} else if(categoryType == "4") {
			redis.zrem(REDIS_KEY_REPORT_CONSTELLATION + categoryId, pid)	
		} else if(categoryType == "5") {
			redis.zrem(REDIS_KEY_REPORT_MOTION + categoryId, pid)	
		} else {
			redis.zrem(REDIS_KEY_REPORT + categoryId, pid)
		}

		redis.hdel(REDIS_KEY_POST, pid)

		redis.zrevrange(REDIS_KEY_REPLY_POST_ID + pid, 0, -1).map { ut =>
			ut.map{ u => u.utf8String}.map { rid =>
				redis.hdel(REDIS_KEY_REPLY_POST, rid)
				redis.hdel(REDIS_KEY_REPLY_POST_ID + pid, rid)
			}
		}
		Ok("ok")
	}

	def createReplyPost(uid : String, lid : String, data : String, pid : String) = Action { request =>
		Logger.info( s"createReplyPost uid:${uid}, lid:${lid}, data:${data}, pid:${pid}")

		if(BlockApplication.isBlock(uid, lid)) {
			Logger.info("blocker uid " + uid + " want to createReplyPost")
			Status(509)("error")  
		} else {
		  	val now = System.currentTimeMillis
		  	val rid = java.util.UUID.randomUUID.toString
		  	redis.zadd(REDIS_KEY_REPLY_POST_ID + pid, (now, rid))	

			val replyData = s"""{"uid":"${uid}", "lid":"${lid}", "data":"${JSONFormat.quoteString(data)}", "pid":"${pid}", "ts":${now}, "rid":"${rid}"}"""
			redis.hset(REDIS_KEY_REPLY_POST, rid, replyData)

			redis.hget(REDIS_KEY_POST, pid).map { dd => //Future[Option[R]]
				if(dd.isDefined) Logger.info("post exist") else Logger.info("post not exist")
				dd.map { postt =>
					val post = postt.utf8String
					val pp = mapper.readValue[PostItem](post, classOf[PostItem])
					Logger.info("post count" + (pp.reply_count +1))
					redis.hset(REDIS_KEY_POST, pid, mapper.writeValueAsString(PostItem(pp.uid, pp.lid, pp.data, pp.pid, now, pp.with_pic, pp.reply_count +1)))
					redis.zadd(REDIS_KEY_REPORT_ALL, (now, pid))
				}
			}

			updateActive(uid)
			updateOnline(uid)
		  	Ok("")
	  	}
	}	


	def searchReplyPost(pid : String, uid : String) = Action.async {
		updateOnline(uid)
		updateActive(uid)
		for {
			ut <- redis.zrevrange(REDIS_KEY_REPLY_POST_ID + pid, 0, -1) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_REPLY_POST, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}		
	}

	def searchAllPost(page: Int, uid : String) = Action.async {
		Logger.info(s"search searchAllPost:page:${page}")
		updateOnline(uid)
		updateActive(uid)

		val start =  (NUM_OF_PAGE * page)
		val end = (NUM_OF_PAGE * (page + 1)) - 1

		Logger.debug("start:" + start + ",end:" + end)
		for {
			ut <- redis.zrevrange(REDIS_KEY_REPORT_ALL, start, end) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_POST, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}

	def postAllPost() {
		for( categoryType <- 0 to 4) {
			for( categoryId <- 0 to 20) {
				val redisKey = if(categoryType == 0) { 
					REDIS_KEY_REPORT_INTEREST + categoryId
				} else if(categoryType == 1) {
					REDIS_KEY_REPORT_PLACE + categoryId
				} else if(categoryType == 2)  {
					REDIS_KEY_REPORT_CAREER + categoryId
				} else if(categoryType == 3)  {				
					REDIS_KEY_REPORT_OLD + categoryId
				} else if(categoryType == 4)  {				
					REDIS_KEY_REPORT_CONSTELLATION + categoryId
				} else {				
					REDIS_KEY_REPORT_MOTION + categoryId
				}

				redis.zrangeWithscores(redisKey, 0, -1).map { ff =>//Future[Seq[(R, Double)]]
					ff.foreach { f =>
						val id = f._1.utf8String
						val ts = f._2
						redis.zadd(REDIS_KEY_REPORT_ALL, (ts, id))
					}
				}
			}		
		}
	}

	def searchPost(categoryType : String, categoryId : String, page: Int, uid : String) = Action.async {
		Logger.info(s"search categoryType:${categoryType},categoryId:${categoryId},page:${page}")
		updateOnline(uid)
		updateActive(uid)

		val start =  (NUM_OF_PAGE * page)
		val end = (NUM_OF_PAGE * (page + 1)) - 1

		// val start =  (0 - NUM_OF_PAGE) - (NUM_OF_PAGE * page)
		// val end = -1 - (NUM_OF_PAGE * page)

		val redisKey = if(categoryType == "0") { 
			REDIS_KEY_REPORT_INTEREST + categoryId
		} else if(categoryType == "1") {
			REDIS_KEY_REPORT_PLACE + categoryId
		} else if(categoryType == "2")  {
			REDIS_KEY_REPORT_CAREER + categoryId
		} else if(categoryType == "3")  {				
			REDIS_KEY_REPORT_OLD + categoryId
		} else if(categoryType == "4")  {				
			REDIS_KEY_REPORT_CONSTELLATION + categoryId
		} else if(categoryType == "5")  {				
			REDIS_KEY_REPORT_MOTION + categoryId
		} else {
			REDIS_KEY_REPORT + categoryId
		} 

		Logger.debug("redisKey:" + redisKey+ ",start:" + start + ",end:" + end)
		for {
			ut <- redis.zrevrange(redisKey, start, end) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_POST, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}

	def getPostImg( pid : String) = Action {
		val path = postPath + "/" + pid + ".jpg"
		val ff = new java.io.File(path)
		if(ff.exists)
  			Ok.sendFile(ff)
  		else NotFound
	}	

}