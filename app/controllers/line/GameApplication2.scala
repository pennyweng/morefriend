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



object GameApplication2 extends Controller {
	implicit val system = Akka.system

	val NEXT_TIME = 3600000 * 5l
	val GUESS_NUMBER = 6
	val INIT_BIG = 130
	val INIT_SMALL = 1
	val MIN_PEOPLE = 100

	var isLNFinish = false

	var currentLNBigNumber = INIT_BIG	
	var currentLNSmallNumber = INIT_SMALL	
	var currentLNSNumber = GUESS_NUMBER	
	var currentLNWinNumber = ""
	var currentLNGameSt = 0l
	var currentLNGid = ""
	var currentLNGame = ""

	val GAME_TYPE_LN = "ln"
	val GAME_LN_NAME = "戳戳樂"
	val GAME_LN_INTRO = s"戳一下，立即開獎。看誰先戳到50元商品卡，誰就獲勝" //。目前得獎機率(1/${currentLNBigNumber})
	val GAME_LN_REWARD = """獲勝者可獲得7-11的50元商品卡。"""

	val REDIS_KEY_GAME_LN_CURRENT = "GLNC"
	val REDIS_KEY_GAME_LN_BIG = "GLNB"
	val REDIS_KEY_GAME_LN_SMALL = "GLNS"
	val REDIS_KEY_GAME_LN_START_TIME = "GLNST"
	val REDIS_KEY_GAME_LN_COUNT = "GLNCU"
	val REDIS_KEY_GAME_LN_FINISH = "GLNF"
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))
	

	def resetLNGame(uid : String) = Action {
		Logger.info(s"resetLNGame uid:${uid}")
		Ok(createLNGame())	
	}

	def createLNGame() = {
		Logger.info(s"createLNGame")
		isLNFinish = false

		// val ss = (1 to currentLNBigNumber).map { e => ( new java.util.Random().nextInt(1000), e)}.toArray
		// scala.util.Sorting.quickSort(ss)
		currentLNWinNumber = new java.util.Random().nextInt(currentLNBigNumber).toString//ss.take(6).map{ _._2 }.mkString(",")
		currentLNGid = java.util.UUID.randomUUID.toString
		currentLNGameSt = System.currentTimeMillis
		currentLNGame = s"""{"gid":"${currentLNGid}", 
		"name":"${GAME_LN_NAME}",
		"st":${currentLNGameSt}, 
		"gtype":"${GAME_TYPE_LN}",
		"et":0,
		"introduction":"${GAME_LN_INTRO}", 
		"reward":"${GAME_LN_REWARD}", 
		"is_finish":false,
		"has_win":false,
		"winner":null}"""

		currentLNSNumber = GUESS_NUMBER
		currentLNBigNumber = INIT_BIG	
		currentLNSmallNumber = INIT_SMALL	

		redis.set(REDIS_KEY_GAME_LN_CURRENT, currentLNGid)	
		redis.set(REDIS_KEY_GAME_LN_BIG, currentLNBigNumber)
		redis.set(REDIS_KEY_GAME_LN_SMALL, currentLNSmallNumber)
		redis.set(REDIS_KEY_GAME_LN_START_TIME, currentLNGameSt)
		redis.set(REDIS_KEY_GAME_LN_COUNT, currentLNSNumber)
		
		redis.hset(REDIS_KEY_GAME_WIN_NUMBER, currentLNGid, currentLNWinNumber)
		redis.hset(REDIS_KEY_GAME_TOTALS, currentLNGid, currentLNGame)
		currentLNGame
	}

	def playLNGame(gid : String, uid : String, desc : String, lid : String) = Action.async {
		Logger.info(s"current gid:${gid}, uid:${uid}, desc:${desc},  lid:${lid}")
		
		val guess = new java.util.Random().nextInt(currentLNBigNumber).toString
		val now = System.currentTimeMillis

		for {
			tt <- redis.hget(REDIS_KEY_GAME_CHECK_TIME + gid, uid)
			ae <- redis.hlen(REDIS_KEY_GAME_HISTORY + gid)
		} yield {
			if(currentLNGid != gid) {
				Logger.info(s"current gid:${currentLNGid}, your gid ${gid}")
				Ok("err:1")
			} else if(isLNFinish) {	
				Logger.info(s"current gid:${isLNFinish} finish")
				Ok("err:2")
			} 
			else if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
				Logger.info(s"time is not over,current gid:${currentLNGid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
				Ok("err:3:" + sss)
			} else {
				if(guess == currentLNWinNumber && ae >= MIN_PEOPLE) {
					Logger.info(s"you guess the number, gid ${gid}")
					redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##戳到大獎##" + desc)

					isLNFinish = true
					redis.hset(REDIS_KEY_GAME_WINER, gid, uid)
					currentLNGame = s"""{"game_info":{"gid":"${gid}", 
								"name":"${GAME_LN_NAME}",
								"st":${currentLNGameSt}, 
								"gtype":"${GAME_TYPE_LN}",
								"et":${System.currentTimeMillis},
								"introduction":"${GAME_LN_INTRO}", 
								"reward":"${GAME_LN_REWARD}", 
								"is_finish":true,
								"has_win":true,
								"total":${ae},
								"winner_n":"${guess}",
								"winner_lid":"${lid}",
								"winner":"${uid}"}}"""
					redis.hset(REDIS_KEY_GAME_LN_FINISH, gid, currentLNGame)
					redis.hset(REDIS_KEY_GAME_TOTALS, gid, currentLNGame)
					redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
					createLNGame()						


					Ok(s"""${currentLNSNumber}##${0}##winner""")
				} else { 
					redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##銘謝惠顧##" + desc)
					redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
					Logger.info(s"""current LN game currentLNGid:${currentLNGid},
						currentLNWinNumber:${currentLNWinNumber},
						currentLNBigNumber:${currentLNBigNumber}, 
						currentLNSmallNumber:${currentLNSmallNumber}, 
						isLNFinish:${isLNFinish},
						currentLNGame:${currentLNGame},
						currentLNGameSt:${currentLNGameSt} """)

					Ok(s"""${guess}""")
				}	
			} 
		}
	}

	def reloadCurrentLNGame() {
		Logger.info(s"run reloadCurrentGuessGame")
		for{
			n1 <- redis.get(REDIS_KEY_GAME_LN_BIG) // Future[Option[R]]
			n2 <- redis.get(REDIS_KEY_GAME_LN_SMALL)
			n3 <- redis.get(REDIS_KEY_GAME_LN_CURRENT)
			n4 <- redis.get(REDIS_KEY_GAME_LN_START_TIME)
			n8 <- redis.get(REDIS_KEY_GAME_LN_COUNT)
			n5 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WIN_NUMBER, n3.get.utf8String) else Future.successful(None)
			n6 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_TOTALS, n3.get.utf8String) else Future.successful(None)
			n7 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WINER, n3.get.utf8String) else Future.successful(None)
		} {
			if(n3.isDefined) currentLNGid = n3.get.utf8String
			if(n5.isDefined) currentLNWinNumber = n5.get.utf8String
			if(n1.isDefined) currentLNBigNumber = n1.get.utf8String.toInt
			if(n2.isDefined) currentLNSmallNumber = n2.get.utf8String.toInt
			if(n7.isDefined) isLNFinish = true else isLNFinish = false
			if(n6.isDefined) currentLNGame = n6.get.utf8String
			if(n4.isDefined) currentLNGameSt = n4.get.utf8String.toLong
			if(n8.isDefined) currentLNSNumber = n8.get.utf8String.toInt
			Logger.info(s"""current LN game currentLNGid:${currentLNGid},
				currentLNWinNumber:${currentLNWinNumber},
				currentLNBigNumber:${currentLNBigNumber}, 
				currentLNSmallNumber:${currentLNSmallNumber}, 
				isLNFinish:${isLNFinish},
				currentLNGame:${currentLNGame},
				currentLNGameSt:${currentLNGameSt} """)		
		}
	}

	def getAllFinishGames( uid :String) = Action.async {
		val guess = redis.hgetall(REDIS_KEY_GAME_GUESS_FINISH).map { all => //Future[Map[String, R]]
			all.map { k => 
					val jj = mapper.readTree(k._2.utf8String)
					if(jj.get("game_info").get("et") != null)
						(jj.get("game_info").get("et").asText, k._2.utf8String)
					else ("0", k._2.utf8String)
			}.toArray
		}

		val ab = redis.hgetall(GameApplication1.REDIS_KEY_GAME_AB_FINISH).map { all => //Future[Map[String, R]]
			all.map { k => 
					val jj = mapper.readTree(k._2.utf8String)
					if(jj.get("game_info").get("et") != null)
						(jj.get("game_info").get("et").asText, k._2.utf8String)
					else ("0", k._2.utf8String)
				}.toArray
		}

		val nl = redis.hgetall(GameApplication2.REDIS_KEY_GAME_LN_FINISH).map { all => //Future[Map[String, R]]
			all.map { k => 
					val jj = mapper.readTree(k._2.utf8String)
					if(jj.get("game_info").get("et") != null)
						(jj.get("game_info").get("et").asText, k._2.utf8String)
					else ("0", k._2.utf8String)
				}.toArray
		}

		for {
			aa <- guess
			bb <- ab
			cc <- nl
		} yield {
			val ee = aa ++ bb ++ cc
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)			
		}
	}

	def getCurrentAllGame(uid : String) = Action {
		Logger.info(s"getCurrentAllGame uid:${uid}")
		Ok(s"[]")	
	}

	def getCurrentAllGames(uid : String) = Action.async {
		Logger.info(s"getCurrentAllGame uid:${uid}")

		val guessGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication.currentGuessGid).map { ee =>
			s"""{"game_info":${GameApplication.currentGuessGame},"small":${GameApplication.currentGuessSmallNumber}, 
			"big":${GameApplication.currentGuessBigNumber},"count":${ee} }"""
		}

		val abGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication1.currentABGid).map { ee =>
			s"""{"game_info":${GameApplication1.currentABGame},"small":${GameApplication1.currentABSmallNumber}, 
			"big":${GameApplication1.currentABBigNumber},"count":${ee},"number":${GameApplication1.currentABSNumber} }"""
		}	

		val lnGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication2.currentLNGid).map { ee =>
			s"""{"game_info":${currentLNGame},"small":${GameApplication2.currentLNSmallNumber}, 
			"big":${GameApplication2.currentLNBigNumber},"count":${ee},"number":${GameApplication2.currentLNSNumber} }"""
		}	

		for {
			a <- guessGame
			b <- abGame
			c <- lnGame
		} yield {
			Ok(s"[${a},${b},${c}]")	
		}
	}	
}
