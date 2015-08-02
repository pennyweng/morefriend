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

object AccountApplication extends Controller {
	implicit val system = Akka.system
	val redis = RedisClient(REDIS_HOST, 6379)
	
	val uploadPath = "./users"
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
	
	def getProfile(uid : String)  = Action.async {
		Logger.info( s"get Profile uid:${uid}")
		updateHot(uid)

		redis.hget(REDIS_KEY_USER, uid).map { ee =>
			if(ee.isDefined) Ok(ee.get.utf8String)
			else NotFound
		}
	}

	def hideProfile(uid : String, isHide: Boolean) = Action {
		if(isHide) {
			redis.hset(REDIS_KEY_HIDE, uid, "1")
			redis.zrem(REDIS_KEY_ONLINE, uid)
		} else redis.hdel(REDIS_KEY_HIDE, uid)
		Ok("")
	}

	def uploadImg(uid: String) = Action(parse.temporaryFile) { request =>
		val path = uploadPath + "/" + uid + ".jpg"
		Logger.info(" file upload path " + path)
	  	request.body.moveTo(new java.io.File(path), true)
	  	Ok("")
	}

	def createProfile(uid : String, nickname : String, lineId: String, interests: String, places : String, careers: String, olds: String, s: String, constellations: String, motions : String) = Action(parse.temporaryFile) { request =>
		Logger.info( s"createProfile uid:${uid}, nickname:${nickname}, lineId:${lineId}, interest:${interests}, places:${places}, careers:${careers}, olds:${olds}, constellations:${constellations}, motions:${motions}")
		if(BlockApplication.isBlock(uid, lineId)) {
			Status(509)("")
		} else { 

		val path = uploadPath + "/" + uid + ".jpg"
		Logger.info(" file upload path " + path)
	  	request.body.moveTo(new java.io.File(path), true)

	  	val now = System.currentTimeMillis
		val userData = s"""{"uid":"${uid}", "nn":"${JSONFormat.quoteString(nickname)}", "lid":"${JSONFormat.quoteString(lineId)}", "s":"${s}", "in":"${interests}", "pn":"${places}", "cn":"${careers}", "on":"${olds}", "constel":"${constellations}","mo":"${motions}"} """
		redis.hexists(REDIS_KEY_USER, uid).map { isExits =>
			if(!isExits) redis.zadd(REDIS_KEY_TODAY_USER, (now, uid))
		}
		
		redis.hset(REDIS_KEY_USER, uid, userData)

		redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
			if(!ss.isDefined) {
				redis.zadd(REDIS_KEY_ACCOUNT, (now, uid))
			}
		}

		if(s == "M") {
			redis.zscore(REDIS_KEY_ACCOUNT_MAN, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_ACCOUNT_MAN, (now, uid))
				}
			}
			redis.zrem(REDIS_KEY_ACCOUNT_WOMEN, uid)
		} else if (s == "F") {
			redis.zscore(REDIS_KEY_ACCOUNT_WOMEN, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_ACCOUNT_WOMEN, (now, uid))
				}
			}
			redis.zrem(REDIS_KEY_ACCOUNT_MAN, uid)
		} 


		interests.split(",").foreach { interest =>
			redis.zscore(REDIS_KEY_INTEREST + interest, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_INTEREST + interest, (now, uid))
				}
			}
			if(s == "M") {
				redis.zscore(REDIS_KEY_INTEREST_M + interest, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_INTEREST_M + interest, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_INTEREST_F + interest, uid)
			} else {
				redis.zscore(REDIS_KEY_INTEREST_F + interest, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_INTEREST_F + interest, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_INTEREST_M + interest, uid)
			}
		}

		places.split(",").foreach { place =>
			redis.zscore(REDIS_KEY_PLACE + place, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_PLACE + place, (now, uid))
				}
			}
			if(s == "M") {
				redis.zscore(REDIS_KEY_PLACE_M + place, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_PLACE_M + place, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_PLACE_F + place, uid)
			} else {
				redis.zscore(REDIS_KEY_PLACE_F + place, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_PLACE_F + place, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_PLACE_M + place, uid)
			}
		}

		careers.split(",").foreach { career =>
			redis.zscore(REDIS_KEY_CAREER + career, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_CAREER + career, (now, uid))
				}
			}
			if(s == "M") {
				redis.zscore(REDIS_KEY_CAREER_M + career, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_CAREER_M + career, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_CAREER_F + career, uid)
			} else {
				redis.zscore(REDIS_KEY_CAREER_F + career, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_CAREER_F + career, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_CAREER_M + career, uid)
			}
		}

		olds.split(",").foreach { old =>
			redis.zscore(REDIS_KEY_OLD + old, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_OLD + old, (now, uid))
				}
			}
			if(s == "M") {
				redis.zscore(REDIS_KEY_OLD_M + old, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_OLD_M + old, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_OLD_F + old, uid)
			} else {
				redis.zscore(REDIS_KEY_OLD_F + old, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_OLD_F + old, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_OLD_M + old, uid)
			}
		}

		constellations.split(",").foreach { constellation =>
			redis.zscore(REDIS_KEY_CONSTELLATION + constellation, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_CONSTELLATION + constellation, (now, uid))
				}
			}
			if(s == "M") {
				redis.zscore(REDIS_KEY_CONSTELLATION_M + constellation, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_CONSTELLATION_M + constellation, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_CONSTELLATION_F + constellation, uid)
			} else {
				redis.zscore(REDIS_KEY_CONSTELLATION_F + constellation, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_CONSTELLATION_F + constellation, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_CONSTELLATION_M + constellation, uid)
			}
		}

		motions.split(",").foreach { motion =>
			redis.zscore(REDIS_KEY_MOTION + motion, uid).map { ss => //Future[Option[Double]]
				if(!ss.isDefined) {
					redis.zadd(REDIS_KEY_MOTION + motion, (now, uid))
				}
			}
			if(s == "M") {
				redis.zscore(REDIS_KEY_MOTION_M + motion, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_MOTION_M + motion, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_MOTION_F + motion, uid)
			} else {
				redis.zscore(REDIS_KEY_MOTION_F + motion, uid).map { ss => //Future[Option[Double]]
					if(!ss.isDefined) {
						redis.zadd(REDIS_KEY_MOTION_F + motion, (now, uid))
					}
				}
				redis.zrem(REDIS_KEY_MOTION_M + motion, uid)
			}
		}


		val intids = interests.split(",")
		ALL_INTERESTS_ID.filter( id => !intids.contains(id)).foreach { id =>
			redis.zrem(REDIS_KEY_INTEREST + id, uid)
			redis.zrem(REDIS_KEY_INTEREST_M + id, uid)
			redis.zrem(REDIS_KEY_INTEREST_F + id, uid)

		}

		val placeids = places.split(",")
		ALL_PLACES_ID.filter( id => !placeids.contains(id)).foreach { id =>
			redis.zrem(REDIS_KEY_PLACE + id, uid)
			redis.zrem(REDIS_KEY_PLACE_M + id, uid)
			redis.zrem(REDIS_KEY_PLACE_F + id, uid)

		}

		val careerIds = careers.split(",")
		ALL_CAREERS_ID.filter( id => !careerIds.contains(id)).foreach { id =>
			redis.zrem(REDIS_KEY_CAREER + id, uid)
			redis.zrem(REDIS_KEY_CAREER_M + id, uid)
			redis.zrem(REDIS_KEY_CAREER_F + id, uid)			
		}

		val oldsids = olds.split(",")
		ALL_OLDS_ID.filter( id => !oldsids.contains(id)).foreach { id =>
			redis.zrem(REDIS_KEY_OLD + id, uid)
			redis.zrem(REDIS_KEY_OLD_M + id, uid)
			redis.zrem(REDIS_KEY_OLD_F + id, uid)			
		}

		val consids = constellations.split(",")
		ALL_CONSTELLATION_ID.filter( id => !consids.contains(id)).foreach { id =>
			redis.zrem(REDIS_KEY_CONSTELLATION + id, uid)
			redis.zrem(REDIS_KEY_CONSTELLATION_M + id, uid)
			redis.zrem(REDIS_KEY_CONSTELLATION_F + id, uid)			
		}

		val motionids = motions.split(",")
		ALL_MOTION_ID.filter( id => !motionids.contains(id)).foreach { id =>
			redis.zrem(REDIS_KEY_MOTION + id, uid)
			redis.zrem(REDIS_KEY_MOTION_M + id, uid)
			redis.zrem(REDIS_KEY_MOTION_F + id, uid)			
		}

		controllers.line.TraceApplication.updateOnline(uid)

	  	Ok("")
	  }
	}

	def delProfile(uid : String) {
		val path = uploadPath + "/" + uid + ".jpg"
		if(new java.io.File(path).exists) new java.io.File(path).delete

		ALL_INTERESTS_ID.foreach { id =>
			redis.zrem(REDIS_KEY_INTEREST + id, uid)
			redis.zrem(REDIS_KEY_INTEREST_M + id, uid)
			redis.zrem(REDIS_KEY_INTEREST_F + id, uid)
		}

		ALL_PLACES_ID.foreach { id =>
			redis.zrem(REDIS_KEY_PLACE + id, uid)
			redis.zrem(REDIS_KEY_PLACE_M + id, uid)
			redis.zrem(REDIS_KEY_PLACE_F + id, uid)
		}

		ALL_CAREERS_ID.foreach { id =>
			redis.zrem(REDIS_KEY_CAREER + id, uid)
			redis.zrem(REDIS_KEY_CAREER_M + id, uid)
			redis.zrem(REDIS_KEY_CAREER_F + id, uid)
		}

		ALL_OLDS_ID.foreach { id =>
			redis.zrem(REDIS_KEY_OLD + id, uid)
			redis.zrem(REDIS_KEY_OLD_M + id, uid)
			redis.zrem(REDIS_KEY_OLD_F + id, uid)
		}	

		ALL_CONSTELLATION_ID.foreach { id =>
			redis.zrem(REDIS_KEY_CONSTELLATION + id, uid)
			redis.zrem(REDIS_KEY_CONSTELLATION_M + id, uid)
			redis.zrem(REDIS_KEY_CONSTELLATION_F + id, uid)
		}	

		ALL_MOTION_ID.foreach { id =>
			redis.zrem(REDIS_KEY_MOTION + id, uid)
			redis.zrem(REDIS_KEY_MOTION_M + id, uid)
			redis.zrem(REDIS_KEY_MOTION_F + id, uid)
		}

		redis.zrem(REDIS_KEY_ACCOUNT, uid)
		redis.zrem(REDIS_KEY_ACCOUNT_MAN, uid)
		redis.zrem(REDIS_KEY_ACCOUNT_WOMEN, uid)

		redis.hdel(REDIS_KEY_USER, uid)	

	}

	def deleteprofile( uid : String ) = Action {
		delProfile(uid)			
		Ok("")
	}
	
	def getAccountImg( uid : String) = Action {
		val path = uploadPath + "/" + uid + ".jpg"
		val ff = new java.io.File(path)
		if(ff.exists)
  			Ok.sendFile(ff)
  		else NotFound
	}	


	def register(uid: String, gcmId : String) = Action {
	  Logger.debug("register gcm => userId:" + uid + ",gcmId:" + gcmId)
	  redis.hset(REDIS_KEY_GCM, uid, gcmId)
	  Ok("ok")
	}

	def unRegister(uid: String) = Action {
	  Logger.debug("unregister gcm => userId:" + uid)
	  redis.hdel(REDIS_KEY_GCM, uid)
	  Ok("ok")
	}	


// zrangeWithscores[R](key: String, start: Long, stop: Long)(implicit arg0: ByteStringDeserializer[R]): Future[Seq[(R, Double)]]
	def generateAccount() {
		val now = System.currentTimeMillis

		redis.zrangeWithscores(REDIS_KEY_ACCOUNT, 0, now).map { e =>
			e.map { data =>
				val ts = data._2
				val uid = data._1.utf8String

				redis.hget(REDIS_KEY_USER, uid).map { user => //Option[R]]
					if(user.isDefined) {
						val jj = mapper.readTree(user.get.utf8String)
						val s = jj.get("s").asText
						if(s == "M") {
							redis.zscore(REDIS_KEY_ACCOUNT_MAN, uid).map { ss => //Future[Option[Double]]
								if(!ss.isDefined) {
									redis.zadd(REDIS_KEY_ACCOUNT_MAN, (ts, uid))
								}
							}
						} else if (s == "F") {
							redis.zscore(REDIS_KEY_ACCOUNT_WOMEN, uid).map { ss => //Future[Option[Double]]
								if(!ss.isDefined) {
									redis.zadd(REDIS_KEY_ACCOUNT_WOMEN, (ts, uid))
								}
							}
						} 					
					}
				}
			}			
		}

		// ALL_INTERESTS_ID.foreach { id =>
		// 	redis.zrangeWithscores(REDIS_KEY_INTEREST + id, 0, now).map { e =>//Seq[(R, Double)
		// 		e.map { data =>
		// 			val ts = data._2
		// 			val uid = data._1.utf8String
		// 			redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
		// 				if(!ss.isDefined) {
		// 					redis.zadd(REDIS_KEY_ACCOUNT, (ts, uid))
		// 				}
		// 			}
		// 		}
		// 	}
		// }

		// ALL_PLACES_ID.foreach { id =>
		// 	redis.zrangeWithscores(ALL_PLACES_ID + id, 0, now).map { e =>//Seq[(R, Double)
		// 		e.map { data =>
		// 			val ts = data._2
		// 			val uid = data._1.utf8String
		// 			redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
		// 				if(!ss.isDefined) {
		// 					redis.zadd(REDIS_KEY_ACCOUNT, (ts, uid))
		// 				}
		// 			}
		// 		}
		// 	}
		// }

		// ALL_CAREERS_ID.foreach { id =>
		// 	redis.zrangeWithscores(ALL_CAREERS_ID + id, 0, now).map { e =>//Seq[(R, Double)
		// 		e.map { data =>
		// 			val ts = data._2
		// 			val uid = data._1.utf8String
		// 			redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
		// 				if(!ss.isDefined) {
		// 					redis.zadd(REDIS_KEY_ACCOUNT, (ts, uid))
		// 				}
		// 			}
		// 		}
		// 	}
		// }
		
		// ALL_OLDS_ID.foreach { id =>
		// 	redis.zrangeWithscores(ALL_OLDS_ID + id, 0, now).map { e =>//Seq[(R, Double)
		// 		e.map { data =>
		// 			val ts = data._2
		// 			val uid = data._1.utf8String
		// 			redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
		// 				if(!ss.isDefined) {
		// 					redis.zadd(REDIS_KEY_ACCOUNT, (ts, uid))
		// 				}
		// 			}
		// 		}
		// 	}
		// }

		// ALL_CONSTELLATION_ID.foreach { id =>
		// 	redis.zrangeWithscores(ALL_CONSTELLATION_ID + id, 0, now).map { e =>//Seq[(R, Double)
		// 		e.map { data =>
		// 			val ts = data._2
		// 			val uid = data._1.utf8String
		// 			redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
		// 				if(!ss.isDefined) {
		// 					redis.zadd(REDIS_KEY_ACCOUNT, (ts, uid))
		// 				}
		// 			}
		// 		}
		// 	}
		// }	
		

		// ALL_MOTION_ID.foreach { id =>
		// 	redis.zrangeWithscores(ALL_MOTION_ID + id, 0, now).map { e =>//Seq[(R, Double)
		// 		e.map { data =>
		// 			val ts = data._2
		// 			val uid = data._1.utf8String
		// 			redis.zscore(REDIS_KEY_ACCOUNT, uid).map { ss => //Future[Option[Double]]
		// 				if(!ss.isDefined) {
		// 					redis.zadd(REDIS_KEY_ACCOUNT, (ts, uid))
		// 				}
		// 			}
		// 		}
		// 	}
		// }

	}	
}