package models

import play.api._
import play.api.mvc._
import play.Logger

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.JavaConversions._

import akka.actor.{ActorSystem, Props}

import redis.RedisClient
import play.api.libs.concurrent.Akka
import scala.concurrent.Future

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._


@JsonIgnoreProperties(ignoreUnknown=true)
case class LineUserInfo @JsonCreator() (
	@scala.reflect.BeanProperty @JsonProperty("uid") uid : String, 
	@scala.reflect.BeanProperty @JsonProperty("nn")  nn : String,
	@scala.reflect.BeanProperty @JsonProperty("lid") lid : String,
	@scala.reflect.BeanProperty @JsonProperty("s") s : String,
	@scala.reflect.BeanProperty @JsonProperty("in") in : String = "",
	@scala.reflect.BeanProperty @JsonProperty("pn") pn : String = "",
	@scala.reflect.BeanProperty @JsonProperty("cn") cn : String = "",
	@scala.reflect.BeanProperty @JsonProperty("on") on : String = ""

	)

@JsonIgnoreProperties(ignoreUnknown=true)
case class OriginLineUserInfo @JsonCreator() (
	@scala.reflect.BeanProperty @JsonProperty("uid") uid : String, 
	@scala.reflect.BeanProperty @JsonProperty("nn")  nn : String,
	@scala.reflect.BeanProperty @JsonProperty("lid") lid : String,
	@scala.reflect.BeanProperty @JsonProperty("s") s : String

	)

object Line {
	implicit val system = Akka.system
	val redis = RedisClient("www.jookershop.com", 6379)

	val REDIS_KEY_USER = "USR"
	val REDIS_KEY_INTEREST = "IT:"
	val REDIS_KEY_INTEREST_M = "ITM:"
	val REDIS_KEY_INTEREST_F = "ITF:"

	val REDIS_KEY_PLACE = "PL:"
	val REDIS_KEY_PLACE_M = "PLM:"
	val REDIS_KEY_PLACE_F = "PLF:"	

	val REDIS_KEY_CAREER = "CA:"
	val REDIS_KEY_CAREER_M = "CAM:"
	val REDIS_KEY_CAREER_F = "CAF:"

	val REDIS_KEY_OLD = "OL:"
	val REDIS_KEY_OLD_M = "OLM:"
	val REDIS_KEY_OLD_F = "OLF:"

	val REDIS_KEY_CHAT = "CHAT"

	val ALL_INTERESTS_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15")
	val ALL_PLACES_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15","16","17","18")
	val ALL_CAREERS_ID = List("1", "2", "3", "4", "5", "6","7","8","9","10","11","12","13", "14","15","16","17")
	val ALL_OLDS_ID = List("1", "2", "3", "4", "5", "6","7","8")

	val mapper = new ObjectMapper()


	def updateAllUserProfile() {
		Logger.info("run updateAllUserProfile")

		redis.hgetall(REDIS_KEY_USER).map { ee1 => //Map[String, R]
			Logger.info("ee1 size" + ee1.size)
			var count1 = 0
			var count2 = 0

			ee1.map { ee =>
				try {
					val lineUserInfo = mapper.readValue[LineUserInfo](ee._2.utf8String, classOf[LineUserInfo])
					val aai = ALL_INTERESTS_ID.map { id =>
						redis.zscore(REDIS_KEY_INTEREST + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}
					}

					val aap = ALL_PLACES_ID.map { id =>
						redis.zscore(REDIS_KEY_PLACE + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}
					}

					val aac = ALL_CAREERS_ID.map { id =>
						redis.zscore(REDIS_KEY_CAREER + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}						
					}

					val aao = ALL_OLDS_ID.map { id =>
						redis.zscore(REDIS_KEY_OLD + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}						
					}

					for{
						p1 <- Future.sequence(aai)
						p2 <- Future.sequence(aap)
						p3 <- Future.sequence(aac)
						p4 <- Future.sequence(aao)
					} {
						val luio = LineUserInfo(lineUserInfo.uid, lineUserInfo.nn, lineUserInfo.lid, lineUserInfo.s, 
							p1.flatten.mkString(","), p2.flatten.mkString(","), p3.flatten.mkString(","), p4.flatten.mkString(","))
						redis.hset(REDIS_KEY_USER, lineUserInfo.uid, mapper.writeValueAsString(luio))
						count1 = count1 + 1
						println("count1:" + count1)
					}
				} catch {
					case e => println("")
				}
			}



			ee1.map { ee =>
				try {
					val lineUserInfo = mapper.readValue[OriginLineUserInfo](ee._2.utf8String, classOf[OriginLineUserInfo])
					val aai = ALL_INTERESTS_ID.map { id =>
						redis.zscore(REDIS_KEY_INTEREST + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}
					}

					val aap = ALL_PLACES_ID.map { id =>
						redis.zscore(REDIS_KEY_PLACE + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}
					}

					val aac = ALL_CAREERS_ID.map { id =>
						redis.zscore(REDIS_KEY_CAREER + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}						
					}

					val aao = ALL_OLDS_ID.map { id =>
						redis.zscore(REDIS_KEY_OLD + id, lineUserInfo.uid).map { aa1 =>
							if(aa1.isDefined) Some(id) else None
						}						
					}

					for{
						p1 <- Future.sequence(aai)
						p2 <- Future.sequence(aap)
						p3 <- Future.sequence(aac)
						p4 <- Future.sequence(aao)
					} {
						val luio = LineUserInfo(lineUserInfo.uid, lineUserInfo.nn, lineUserInfo.lid, lineUserInfo.s, 
							p1.flatten.mkString(","), p2.flatten.mkString(","), p3.flatten.mkString(","), p4.flatten.mkString(","))
						redis.hset(REDIS_KEY_USER, lineUserInfo.uid, mapper.writeValueAsString(luio))
						count2 = count2 + 1
						println("count2:" + count2)
					}
				}
				catch {
					case e => println("ee")
				}
			}


		}

	}	
}