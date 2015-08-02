package controllers

import play.api._
import play.api.mvc._
import play.Logger

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.JavaConversions._

import akka.actor.{ActorSystem, Props}

import redis.api.Limit
import redis.RedisClient
import play.api.libs.concurrent.Akka
import scala.concurrent.Future

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._

import scala.util.parsing.json.JSONFormat

object LineApplication extends Controller {
// 	implicit val system = Akka.system

// 	val uploadPath = "./users"

// 	val chatPath = "./chat"

// 	val NUM_OF_PAGE = 50

// 	//www.jookershop.com
// 	val redis = RedisClient("localhost", 6379)

// 	val REDIS_KEY_USER = "USR"
// 	val REDIS_KEY_INTEREST = "IT:"
// 	val REDIS_KEY_INTEREST_M = "ITM:"
// 	val REDIS_KEY_INTEREST_F = "ITF:"

// 	val REDIS_KEY_PLACE = "PL:"
// 	val REDIS_KEY_PLACE_M = "PLM:"
// 	val REDIS_KEY_PLACE_F = "PLF:"	

// 	val REDIS_KEY_CAREER = "CA:"
// 	val REDIS_KEY_CAREER_M = "CAM:"
// 	val REDIS_KEY_CAREER_F = "CAF:"

// 	val REDIS_KEY_OLD = "OL:"
// 	val REDIS_KEY_OLD_M = "OLM:"
// 	val REDIS_KEY_OLD_F = "OLF:"

// 	val REDIS_KEY_CONSTELLATION = "CL:"
// 	val REDIS_KEY_CONSTELLATION_M = "CLM:"
// 	val REDIS_KEY_CONSTELLATION_F = "CLF:"

// 	val REDIS_KEY_MOTION = "MO:"
// 	val REDIS_KEY_MOTION_M = "MOM:"
// 	val REDIS_KEY_MOTION_F = "MOF:"

// 	val REDIS_KEY_CHAT = "CHAT"

// 	val REDIS_KEY_REPORT_INTEREST = "RPT:"
// 	val REDIS_KEY_REPORT_PLACE = "RPP:"
// 	val REDIS_KEY_REPORT_CAREER = "RPC:"
// 	val REDIS_KEY_REPORT_OLD = "RPO:"
// 	val REDIS_KEY_REPORT_CONSTELLATION = "RPCL:"
// 	val REDIS_KEY_REPORT_MOTION = "RPM:"

// 	val REDIS_KEY_REPORT_ALL = "RPALL:"

// 	val REDIS_KEY_ONLINE = "ONL"

// 	val REDIS_KEY_POST = "POST"
// 	val REDIS_KEY_REPLY_POST = "RPOST"
// 	val REDIS_KEY_REPLY_POST_ID = "RPOSTD:"

// 	val REDIS_KEY_MSG = "LMSG"
// 	val REDIS_KEY_NOTIFICATION = "NOTIFY"

// 	val REDIS_KEY_NEW_MSG = "NMSG:"
// 	val REDIS_KEY_NEW_MSG_NOTI = "NMSGN"
// 	val REDIS_KEY_NEW_MSG_NOTI_LAST_U = "NMSGNU"

// 	val REDIS_KEY_TRACE = "TRACE"
// 	val REDIS_KEY_HIDE = "HIDE"



// 	val ALL_INTERESTS_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15")
// 	val ALL_PLACES_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15","16","17","18")
// 	val ALL_CAREERS_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15","16","17")
// 	val ALL_OLDS_ID = List("1", "2", "3", "4", "5", "6","7","8")
// 	val ALL_CONSTELLATION_ID = List("1", "2", "3", "4", "5", "6","7","8", "9", "10", "11", "12")
// 	val ALL_MOTION_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15","16","17","18", "19","20","21")

// 	val mapper = new ObjectMapper()

// 	case class LineMessage @JsonCreator() (
// 		@scala.beans.BeanProperty @JsonProperty("msg") msg : String, 
// 		@scala.beans.BeanProperty @JsonProperty("from") from : String,
// 		@scala.beans.BeanProperty @JsonProperty("fromLid") fromLid : String,
// 		@scala.beans.BeanProperty @JsonProperty("to") to : String,
// 		@scala.beans.BeanProperty @JsonProperty("toLid") toLid : String,
// 		@scala.beans.BeanProperty @JsonProperty("ts") ts : Long		
// 	)

// 	def updateOnline( uid : String ) {
// 		if(uid != "") {
// 			redis.hexists(REDIS_KEY_HIDE, uid).map { isHide =>
// 				if(!isHide) redis.zadd(REDIS_KEY_ONLINE, (System.currentTimeMillis, uid))
// 			}
// 		}
// 	}

// 	def getProfile(uid : String)  = Action.async {
// 		Logger.info( s"get Profile uid:${uid}")
// 		redis.hget(REDIS_KEY_USER, uid).map { ee =>
// 			if(ee.isDefined) Ok(ee.get.utf8String)
// 			else NotFound
// 		}
// 	}

// 	def hideProfile(uid : String, isHide: Boolean) = Action {
// 		if(isHide) {
// 			redis.hset(REDIS_KEY_HIDE, uid, "1")
// 			redis.zrem(REDIS_KEY_ONLINE, uid)
// 		} else redis.hdel(REDIS_KEY_HIDE, uid)
// 		Ok("")
// 	}

// 	def createProfile(uid : String, nickname : String, lineId: String, interests: String, places : String, careers: String, olds: String, s: String, constellations: String, motions : String) = Action(parse.temporaryFile) { request =>
// 		Logger.info( s"createProfile uid:${uid}, nickname:${nickname}, lineId:${lineId}, interest:${interests}, places:${places}, careers:${careers}, olds:${olds}, constellations:${constellations}, motions:${motions}")

// 		val path = uploadPath + "/" + uid + ".jpg"
// 		Logger.info(" file upload path " + path)
// 	  	request.body.moveTo(new java.io.File(path), true)

// 		val userData = s"""{"uid":"${uid}", "nn":"${JSONFormat.quoteString(nickname)}", "lid":"${JSONFormat.quoteString(lineId)}", "s":"${s}", "in":"${interests}", "pn":"${places}", "cn":"${careers}", "on":"${olds}", "constel":"${constellations}","mo":"${motions}"} """
// 		redis.hset(REDIS_KEY_USER, uid, userData)

// 		interests.split(",").foreach { interest =>
// 			redis.zadd(REDIS_KEY_INTEREST + interest, (System.currentTimeMillis, uid))
// 			if(s == "M")
// 				redis.zadd(REDIS_KEY_INTEREST_M + interest, (System.currentTimeMillis, uid))
// 			else redis.zadd(REDIS_KEY_INTEREST_F + interest, (System.currentTimeMillis, uid))
// 		}

// 		places.split(",").foreach { place =>
// 			redis.zadd(REDIS_KEY_PLACE + place, (System.currentTimeMillis, uid))
// 			if(s == "M")
// 				redis.zadd(REDIS_KEY_PLACE_M + place, (System.currentTimeMillis, uid))
// 			else redis.zadd(REDIS_KEY_PLACE_F + place, (System.currentTimeMillis, uid))
// 		}

// 		careers.split(",").foreach { career =>
// 			redis.zadd(REDIS_KEY_CAREER + career, (System.currentTimeMillis, uid))
// 			if(s == "M")
// 				redis.zadd(REDIS_KEY_CAREER_M + career, (System.currentTimeMillis, uid))
// 			else redis.zadd(REDIS_KEY_CAREER_F + career, (System.currentTimeMillis, uid))
// 		}

// 		olds.split(",").foreach { old =>
// 			redis.zadd(REDIS_KEY_OLD + old, (System.currentTimeMillis, uid))
// 			if(s == "M")
// 				redis.zadd(REDIS_KEY_OLD_M + old, (System.currentTimeMillis, uid))
// 			else redis.zadd(REDIS_KEY_OLD_F + old, (System.currentTimeMillis, uid))
// 		}

// 		constellations.split(",").foreach { old =>
// 			redis.zadd(REDIS_KEY_CONSTELLATION + old, (System.currentTimeMillis, uid))
// 			if(s == "M")
// 				redis.zadd(REDIS_KEY_CONSTELLATION_M + old, (System.currentTimeMillis, uid))
// 			else redis.zadd(REDIS_KEY_CONSTELLATION_F + old, (System.currentTimeMillis, uid))
// 		}

// 		motions.split(",").foreach { motion =>
// 			redis.zadd(REDIS_KEY_MOTION + motion, (System.currentTimeMillis, uid))
// 			if(s == "M")
// 				redis.zadd(REDIS_KEY_MOTION_M + motion, (System.currentTimeMillis, uid))
// 			else redis.zadd(REDIS_KEY_MOTION_F + motion, (System.currentTimeMillis, uid))
// 		}

// 		val intids = interests.split(",")
// 		ALL_INTERESTS_ID.filter( id => !intids.contains(id)).foreach { id =>
// 			redis.zrem(REDIS_KEY_INTEREST + id, uid)
// 			if(s == "M")
// 				redis.zrem(REDIS_KEY_INTEREST_M + id, uid)
// 			else redis.zrem(REDIS_KEY_INTEREST_F + id, uid)

// 		}

// 		val placeids = places.split(",")
// 		ALL_PLACES_ID.filter( id => !placeids.contains(id)).foreach { id =>
// 			redis.zrem(REDIS_KEY_PLACE + id, uid)
// 			if(s == "M")
// 				redis.zrem(REDIS_KEY_PLACE_M + id, uid)
// 			else redis.zrem(REDIS_KEY_PLACE_F + id, uid)

// 		}

// 		val careerIds = careers.split(",")
// 		ALL_CAREERS_ID.filter( id => !careerIds.contains(id)).foreach { id =>
// 			redis.zrem(REDIS_KEY_CAREER + id, uid)
// 			if(s == "M")
// 				redis.zrem(REDIS_KEY_CAREER_M + id, uid)
// 			else redis.zrem(REDIS_KEY_CAREER_F + id, uid)			
// 		}

// 		val oldsids = olds.split(",")
// 		ALL_OLDS_ID.filter( id => !oldsids.contains(id)).foreach { id =>
// 			redis.zrem(REDIS_KEY_OLD + id, uid)
// 			if(s == "M")
// 				redis.zrem(REDIS_KEY_OLD_M + id, uid)
// 			else redis.zrem(REDIS_KEY_OLD_F + id, uid)			
// 		}

// 		val consids = constellations.split(",")
// 		ALL_CONSTELLATION_ID.filter( id => !consids.contains(id)).foreach { id =>
// 			redis.zrem(REDIS_KEY_CONSTELLATION + id, uid)
// 			if(s == "M")
// 				redis.zrem(REDIS_KEY_CONSTELLATION_M + id, uid)
// 			else redis.zrem(REDIS_KEY_CONSTELLATION_F + id, uid)			
// 		}

// 		val motionids = motions.split(",")
// 		ALL_MOTION_ID.filter( id => !motionids.contains(id)).foreach { id =>
// 			redis.zrem(REDIS_KEY_MOTION + id, uid)
// 			if(s == "M")
// 				redis.zrem(REDIS_KEY_MOTION_M + id, uid)
// 			else redis.zrem(REDIS_KEY_MOTION_F + id, uid)			
// 		}


// 		updateOnline(uid)

// 	  	Ok("")
// 	}

// 	def deleteprofile( uid : String ) = Action {
// 		val path = uploadPath + "/" + uid + ".jpg"
// 		if(new java.io.File(path).exists) new java.io.File(path).delete

// 		ALL_INTERESTS_ID.foreach { id =>
// 			redis.zrem(REDIS_KEY_INTEREST + id, uid)
// 			redis.zrem(REDIS_KEY_INTEREST_M + id, uid)
// 			redis.zrem(REDIS_KEY_INTEREST_F + id, uid)
// 		}

// 		ALL_PLACES_ID.foreach { id =>
// 			redis.zrem(REDIS_KEY_PLACE + id, uid)
// 			redis.zrem(REDIS_KEY_PLACE_M + id, uid)
// 			redis.zrem(REDIS_KEY_PLACE_F + id, uid)
// 		}

// 		ALL_CAREERS_ID.foreach { id =>
// 			redis.zrem(REDIS_KEY_CAREER + id, uid)
// 			redis.zrem(REDIS_KEY_CAREER_M + id, uid)
// 			redis.zrem(REDIS_KEY_CAREER_F + id, uid)
// 		}

// 		ALL_OLDS_ID.foreach { id =>
// 			redis.zrem(REDIS_KEY_OLD + id, uid)
// 			redis.zrem(REDIS_KEY_OLD_M + id, uid)
// 			redis.zrem(REDIS_KEY_OLD_F + id, uid)
// 		}	

// 		ALL_CONSTELLATION_ID.foreach { id =>
// 			redis.zrem(REDIS_KEY_CONSTELLATION + id, uid)
// 			redis.zrem(REDIS_KEY_CONSTELLATION_M + id, uid)
// 			redis.zrem(REDIS_KEY_CONSTELLATION_F + id, uid)
// 		}	

// 		ALL_MOTION_ID.foreach { id =>
// 			redis.zrem(REDIS_KEY_MOTION + id, uid)
// 			redis.zrem(REDIS_KEY_MOTION_M + id, uid)
// 			redis.zrem(REDIS_KEY_MOTION_F + id, uid)
// 		}

// 		redis.hdel(REDIS_KEY_USER, uid)					
// 		Ok("")
// 	}



// 	def searchCategoryCount(categoryType : String, s : String, uid : String) = Action.async {
// 		Logger.info(s"search searchCategoryCount categoryType:${categoryType}")
// 		updateOnline(uid)

// 		val prefixKeys = if(categoryType == "0") {
// 			if(s == "M")
// 				ALL_INTERESTS_ID.map { id => REDIS_KEY_INTEREST_M + id}.toList
// 			else if(s == "F")
// 				ALL_INTERESTS_ID.map { id => REDIS_KEY_INTEREST_F + id}.toList
// 			else 				
// 				ALL_INTERESTS_ID.map { id => REDIS_KEY_INTEREST + id}.toList
// 		} else if(categoryType == "1") {
// 			if(s == "M")
// 				ALL_PLACES_ID.map { id => REDIS_KEY_PLACE_M + id}.toList
// 			else if(s == "F")				
// 				ALL_PLACES_ID.map { id => REDIS_KEY_PLACE_F + id}.toList
// 			else				
// 				ALL_PLACES_ID.map { id => REDIS_KEY_PLACE + id}.toList
// 		} else if(categoryType == "2") {
// 			if(s == "M")
// 				ALL_CAREERS_ID.map { id => REDIS_KEY_CAREER_M + id}.toList
// 			else if( s== "F")
// 				ALL_CAREERS_ID.map { id => REDIS_KEY_CAREER_F + id}.toList
// 			else
// 				ALL_CAREERS_ID.map { id => REDIS_KEY_CAREER + id}.toList
// 		} else  if(categoryType == "3") {
// 			if(s == "M")
// 				ALL_OLDS_ID.map { id => REDIS_KEY_OLD_M + id}.toList
// 			else if(s == "F")
// 				ALL_OLDS_ID.map { id => REDIS_KEY_OLD_F + id}.toList
// 			else 
// 				ALL_OLDS_ID.map { id => REDIS_KEY_OLD + id}.toList
// 		} else  if(categoryType == "4") {
// 			if(s == "M")
// 				ALL_CONSTELLATION_ID.map { id => REDIS_KEY_CONSTELLATION_M + id}.toList
// 			else if(s == "F")
// 				ALL_CONSTELLATION_ID.map { id => REDIS_KEY_CONSTELLATION_F + id}.toList
// 			else 
// 				ALL_CONSTELLATION_ID.map { id => REDIS_KEY_CONSTELLATION + id}.toList
// 		} else {
// 			if(s == "M")
// 				ALL_MOTION_ID.map { id => REDIS_KEY_MOTION_M + id}.toList
// 			else if(s == "F")
// 				ALL_MOTION_ID.map { id => REDIS_KEY_MOTION_F + id}.toList
// 			else 
// 				ALL_MOTION_ID.map { id => REDIS_KEY_MOTION + id}.toList
// 		}

// 		val a = prefixKeys.map { key => redis.zcount(key).map { e => (key.substring(key.indexOf(":") + 1), e)  } }
		
// 		for {
// 			no <- redis.hget(REDIS_KEY_NOTIFICATION, uid) // Future[Option[R]]
// 			kk <- Future.sequence(a)
// 		} yield {
// 			val resp = kk.map { ee =>
// 				s"""{"id":${ee._1},"count":${ee._2}}"""
// 			}.mkString("[", ",","]")

// 			val resp1 = if(uid != "") {
// 				val nn = if(no.isDefined) s"""{"command":"GET_NOTIFICATION","ts":${no.get.utf8String}}"""  else null
// 				s"""{"r":${resp},"n":${nn}}"""
// 			} else resp
// 			Ok(resp1)
// 		}
// 	}

// 	def getChatroom() = Action.async {
// 		//Future[Map[String, R]]
// 		redis.hgetall(REDIS_KEY_CHAT).map { ee =>
// 			Ok(ee.map { k =>
// 				s""""${k._2.utf8String}" """
// 			}.mkString("[",",", "]"))
// 		}
// 	}

// 	def createChatRoom = {
// 		redis.hset(REDIS_KEY_CHAT, "1", "1@電影聊天室@0@0@#858A3D@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "2", "2@美食聊天室@0@0@#858A3D@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "3", "3@打屁聊天室@26@8@#858A3D@ahappychat@1")
// 		redis.hset(REDIS_KEY_CHAT, "4", "4@北部人聊天室@0@0@#DEAE2C@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "5", "5@中部人聊天室@0@0@#DEAE2C@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "6", "6@南部人聊天室@0@0@#DEAE2C@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "7", "7@20歲聊天室@0@0@#009989@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "8", "8@25歲聊天室@0@0@#009989@ahappychat@0")
// 		redis.hset(REDIS_KEY_CHAT, "9", "9@35歲聊天室@0@0@#009989@ahappychat@0")
// 	}

// 	def search(categoryType : String, categoryId : String, page: Int, s : String, uid : String) = Action.async {
// 		Logger.info(s"search categoryType:${categoryType},categoryId:${categoryId},page:${page}")
// 		updateOnline(uid)

// 		// val start =  (0 - NUM_OF_PAGE) - (NUM_OF_PAGE * page)
// 		// val end = -1 - (NUM_OF_PAGE * page)

// 		val start =  (NUM_OF_PAGE * page)
// 		val end = (NUM_OF_PAGE * (page + 1)) - 1

// 		val redisKey = if(categoryType == "0") { 
// 			if(s == "M")
// 				REDIS_KEY_INTEREST_M + categoryId
// 			else if(s == "F")
// 				REDIS_KEY_INTEREST_F + categoryId
// 			else 
// 				REDIS_KEY_INTEREST + categoryId
// 		} else if(categoryType == "1") {
// 			if(s == "M")
// 				REDIS_KEY_PLACE_M + categoryId
// 			else if(s == "F")				
// 				REDIS_KEY_PLACE_F + categoryId
// 			else
// 				REDIS_KEY_PLACE + categoryId
// 		} else if(categoryType == "2")  {
// 			if(s == "M")
// 				REDIS_KEY_CAREER_M + categoryId
// 			else if( s== "F")
// 				REDIS_KEY_CAREER_F + categoryId
// 			else 
// 				REDIS_KEY_CAREER + categoryId
// 		} else if(categoryType == "3")  {
// 			if( s == "M")
// 				REDIS_KEY_OLD_M + categoryId
// 			else if( s == "F")
// 				REDIS_KEY_OLD_F + categoryId
// 			else 				
// 				REDIS_KEY_OLD + categoryId
// 		} else if(categoryType == "4")  {
// 			if( s == "M")
// 				REDIS_KEY_CONSTELLATION_M + categoryId
// 			else if( s == "F")
// 				REDIS_KEY_CONSTELLATION_F + categoryId
// 			else 				
// 				REDIS_KEY_CONSTELLATION + categoryId
// 		}  else  {
// 			if( s == "M")
// 				REDIS_KEY_MOTION_M + categoryId
// 			else if( s == "F")
// 				REDIS_KEY_MOTION_F + categoryId
// 			else 				
// 				REDIS_KEY_MOTION + categoryId
// 		}

// 		Logger.debug("redisKey:" + redisKey+ ",start:" + start + ",end:" + end)
// 		for {
// 			no <- redis.hget(REDIS_KEY_NOTIFICATION, uid) // Future[Option[R]]
// 			ut <- redis.zrevrange(redisKey, start, end) // //Future[Seq[R]]
// 			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
// 		} yield {
// 			val resp = h.flatten.map { ee =>
// 				ee.utf8String
// 			}.mkString("[", ",", "]")

// 			val resp1 = if(uid != "") {
// 					val nn = if(no.isDefined) s"""{"command":"GET_NOTIFICATION","ts":${no.get.utf8String}}"""  else null
// 					s"""{"r":${resp},"n":${nn}}"""
// 				} else resp
// 			Ok(resp1)
// 		}
// 	}

// 	def getNotify( uid : String ) = Action.async {
// 		 // Future[Option[R]]
// 		redis.hget(REDIS_KEY_NOTIFICATION, uid).map { no =>
// 			val nn = if(no.isDefined) s"""{"n":{"command":"GET_NOTIFICATION","ts":${no.get.utf8String}}}"""  else s"""{"n":null}"""
// 			Ok(nn)
// 		}
// 	}

// 	def getAccountImg( uid : String) = Action {
// 		val path = uploadPath + "/" + uid + ".jpg"
// 		val ff = new java.io.File(path)
// 		if(ff.exists)
//   			Ok.sendFile(ff)
//   		else NotFound
// 	}	

// 	def getChatImg( id : String) = Action {
// 		val path = chatPath + "/" + id + ".png"
// 		val ff = new java.io.File(path)
//   		if(ff.exists)
//   			Ok.sendFile(ff)
//   		else NotFound
// 	}	






// 	def getOnlineUser(uid : String) = Action.async {
// 		Logger.info(s"getOnlineUser uid:${uid}")
// 		updateOnline(uid)
// 		val now = System.currentTimeMillis
// 		val start = Limit(now - (15 * 60 * 1000l))
// 		val end = Limit(now)

// 		redis.zremrangebyscore(REDIS_KEY_ONLINE, Limit(0.toDouble), start)

// 		for {
// 			ut <- redis.zrevrangebyscore(REDIS_KEY_ONLINE, end, start) // //Future[Seq[R]]
// 			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
// 		} yield {
// 			val resp = h.flatten.map { ee =>
// 				ee.utf8String
// 			}.mkString("[", ",", "]")
// 			Ok(resp)
// 		}
// 	}


// 	def getMsg(uid : String) = Action.async {
// 		redis.hdel(REDIS_KEY_NOTIFICATION, uid)
// 		redis.hget(REDIS_KEY_MSG, uid).map{ ff => // Future[Option[R]]
// 			Ok(if(ff.isDefined) {
// 				ff.get.utf8String
// 			} else "[]")
// 		}
// 	}

// 	def putMsg(fromUid : String, fromLid : String, toUid: String, toLid: String, msg : String) = Action {
// 		val now = System.currentTimeMillis
// 		val lmsg = LineMessage(msg, fromUid, fromLid, toUid, toLid, now)

// 		redis.hget(REDIS_KEY_MSG, fromUid).map{ ff => // Future[Option[R]]
// 			if(ff.isDefined) {
// 				val lms = mapper.readValue[Array[LineMessage]](ff.get.utf8String, classOf[Array[LineMessage]])
// 				val newLms = Array(lmsg) ++ lms
// 				val newLmsn = if(newLms.length > 100) newLms.take(100) else newLms  
// 				redis.hset(REDIS_KEY_MSG, fromUid, mapper.writeValueAsString(newLmsn))
// 			} else {
// 				redis.hset(REDIS_KEY_MSG, fromUid, mapper.writeValueAsString(Array(lmsg)))
// 			} 
// 		}

// 		for{
// 			ff <- redis.hget(REDIS_KEY_MSG, toUid)
// 			kk <- saveOldToMsg(lmsg, toUid, ff)
// 			cc <- if(kk) redis.hset(REDIS_KEY_NOTIFICATION, toUid, now) else Future.successful(false)
// 		} {}


// 		// new function
// 		redis.hget(REDIS_KEY_NEW_MSG + fromUid , toUid).map{ ff => // Future[Option[R]]
// 			if(ff.isDefined) {
// 				val lms = mapper.readValue[Array[LineMessage]](ff.get.utf8String, classOf[Array[LineMessage]])
// 				val newLms = lms ++ Array(lmsg) 
// 				val newLmsn = if(newLms.length > 100) newLms.drop(newLms.length - 100) else newLms 
// 				redis.hset(REDIS_KEY_NEW_MSG + fromUid, toUid, mapper.writeValueAsString(newLmsn))
// 			} else {
// 				redis.hset(REDIS_KEY_NEW_MSG + fromUid, toUid, mapper.writeValueAsString( 
// 					Array(lmsg)))
// 			} 
// 		}

// 		// to part
// 		for{
// 			ff <- redis.hget(REDIS_KEY_NEW_MSG + toUid, fromUid)
// 			kk <- saveToMsg(lmsg, ff)
// 			cc <- if(kk) redis.hset(REDIS_KEY_NEW_MSG_NOTI_LAST_U, toUid, mapper.writeValueAsString(lmsg)) 
// 				else Future.successful(false)
// 			dd <- if(cc) saveNewNotification(fromUid, toUid) else Future.successful(false)
// 			ss <- if(dd) redis.hset(REDIS_KEY_NOTIFICATION, toUid, now) else Future.successful(false)	
// 		}{}

// 		Ok("")
// 	}

// 	def addTraceUser(uid : String, toUid: String) = Action {
// 		Logger.info(s"addTraceUser uid:${uid}, toUid:${toUid}")
// 		updateOnline(uid)		

// 		redis.hget(REDIS_KEY_TRACE, uid).map{ ff => // Future[Option[R]]
// 			if(ff.isDefined) {
// 				if(ff.get.utf8String.indexOf(toUid) == -1)
// 				redis.hset(REDIS_KEY_TRACE, uid, ff.get.utf8String + ","  + toUid)
// 			} else {
// 				redis.hset(REDIS_KEY_TRACE, uid, toUid)
// 			} 
// 		}
// 		Ok("")			
// 	}

// 	def getTraceUser(uid : String) = Action.async {
// 		Logger.info(s"getTraceUser uid:${uid}")
// 		updateOnline(uid)
// 		val now = System.currentTimeMillis

// 		for {
// 			nn <- redis.hget(REDIS_KEY_TRACE, uid) // Future[Option[R]]
// 			h <- if(nn.isDefined && nn.get.utf8String.length > 0) redis.hmget(REDIS_KEY_USER, nn.get.utf8String.split(",").toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
// 		} yield {
// 			val resp = h.flatten.map { ee =>
// 				ee.utf8String
// 			}.mkString("[", ",", "]")
// 			Ok(resp)
// 		}
// 	}

// 	def delTraceUser(uid: String, toUid : String ) = Action {
// 		redis.hget(REDIS_KEY_TRACE, uid).map{ ff => // Future[Option[R]]
// 			if(ff.isDefined) {
// 				redis.hset(REDIS_KEY_TRACE, uid, ff.get.utf8String.split(",").filter( _ != toUid).mkString(","))
// 			} 
// 		}
// 		Ok("")
// 	}





// //=======================================
// 	val GAME_TYPE_GUESS = "tg"
// 	val GAME_TYPE_FIVE = "tf"
// 	val REDIS_KEY_GAME_TOTALS = "TGAME"
// 	val REDIS_KEY_GAME_WIN_NUMBER = "GWN"
// 	val REDIS_KEY_GUESS_GAME_PLAY_ITEM = "GPLAYITEM"

// 	val GAME_GUESS_NAME = "終極密碼"
// 	val GAME_GUESS_INTRO = """系統會自動選定一個範圍的數字，讓其餘的人猜數字。如果沒猜中的話，系統要會依猜出的數字將範圍縮小，讓下一個輪到的人繼續猜，直到猜中數字為止。猜中這個數字的人就獲勝。"""
// 	val GAME_GUESS_REWARD = """獲勝者可獲得7-11的100元禮券。"""
// 	val GAME_GUESS_START = "0-10000"

// 	val GAME_FIVE_NAME = "510樓"
// 	val GAME_FIVE_INTRO = """參加者每次最多將樓層向前推進3樓，最少向前推進1樓。例如，前一個人已經將樓層推近到31樓，下一個人最多就可以到34樓。看誰是最後進到510樓的人就是獲勝。"""
// 	val GAME_FIVE_REWARD = """獲勝者可獲得7-11的100元禮券。"""
// 	val GAME_FIVE_START = "0"

// 	def requestNewGame(uid : String, gtype : String) = Action {
// 		val gid = java.util.UUID.randomUUID.toString
// 		if(gtype == GAME_TYPE_GUESS) {
// 			val winNumber = {
// 				val max = GAME_GUESS_START.split("-")(1).toInt
// 				new java.util.Random().nextInt(max)
// 			}
// 			redis.hset(REDIS_KEY_GAME_WIN_NUMBER, gid, winNumber)

// 			val rr = s"""{"gid":"${gid}" 
// 			"name":"${GAME_GUESS_NAME}",
// 			"st":${System.currentTimeMillis}, 
// 			"gtype":"${gtype}",
// 			"et":0, 
// 			"count":0, 
// 			"introduction":"${GAME_GUESS_INTRO}", 
// 			"reward":"${GAME_GUESS_REWARD}", 
// 			"current_number":"${GAME_GUESS_START}", 
// 			"is_finish":false,
// 			"has_win":false,
// 			"winner":null}"""
// 			redis.hset(REDIS_KEY_GAME_TOTALS, gid, rr)
// 		} else if(gtype == GAME_TYPE_FIVE) {
// 			val rr = s"""{"gid":"${gid}" 
// 			"name":"${GAME_FIVE_NAME}",
// 			"st":${System.currentTimeMillis}, 
// 			"gtype":"${gtype}",
// 			"et":0, 
// 			"count":0, 
// 			"introduction":"${GAME_FIVE_INTRO}", 
// 			"reward":"${GAME_FIVE_REWARD}", 
// 			"current_number":"${GAME_FIVE_START}", 
// 			"is_finish":false,
// 			"has_win":false,
// 			"winner":null}"""
// 			redis.hset(REDIS_KEY_GAME_TOTALS, gid, rr)

// 		}
// 		Ok("")
// 	}



// // 	def playGuessGame(uid : String, gtype : String, gid: String, num : String) = Action {
// // 		val playId = java.util.UUID.randomUUID.toString
// // 		redis.hget(REDIS_KEY_GAME_WIN_NUMBER, gid).map { ee =>
// // 			if(ee.isDefined) {
// // 				val winNum = ee.get.utf8String.toInt
// // 				if(winNum == num) {

// // 				}
// // 			}

// // 		}
// // 	}
}