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



object GameApplication1 extends Controller {
	implicit val system = Akka.system

	val NEXT_TIME = 3600000 * 5l
	val GUESS_NUMBER = 6
	val INIT_BIG = 128
	val INIT_SMALL = 1

	var isABFinish = false

	var currentABBigNumber = INIT_BIG	
	var currentABSmallNumber = INIT_SMALL	
	var currentABSNumber = GUESS_NUMBER	
	var currentABWinNumber = ""
	var currentABGameSt = 0l
	var currentABGid = ""
	var currentABGame = ""

	val GAME_TYPE_AB = "ab"
	val GAME_AB_NAME = "1A2B猜數字"
	val GAME_AB_INTRO = s"1到${currentABBigNumber}數字中，猜${currentABSNumber}個不同的數字。若數字與位置跟答案相同則為A，若數字相同位置不同則為B。例如3A1B就是有3個數字猜中，位置也對，有1個數字猜中但位置不對。全部猜對的人就是獲勝。"
	val GAME_AB_REWARD = """獲勝者可獲得7-11的50元商品卡。"""

	val REDIS_KEY_GAME_AB_CURRENT = "GABC"
	val REDIS_KEY_GAME_AB_BIG = "GABB"
	val REDIS_KEY_GAME_AB_SMALL = "GABS"
	val REDIS_KEY_GAME_AB_START_TIME = "GABST"
	val REDIS_KEY_GAME_AB_COUNT = "GABCU"
	val REDIS_KEY_GAME_AB_FINISH = "GAF"
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))
	

	def resetABGame(uid : String) = Action {
		Logger.info(s"resetABGame uid:${uid}")
		Ok(createABGame())	
	}

	def createABGame() = {
		Logger.info(s"createABGame")
		isABFinish = false

		val ss = (1 to currentABBigNumber).map { e => ( new java.util.Random().nextInt(1000), e)}.toArray
		scala.util.Sorting.quickSort(ss)
		currentABWinNumber = ss.take(6).map{ _._2 }.mkString(",")
		currentABGid = java.util.UUID.randomUUID.toString
		currentABGameSt = System.currentTimeMillis
		currentABGame = s"""{"gid":"${currentABGid}", 
		"name":"${GAME_AB_NAME}",
		"st":${currentABGameSt}, 
		"gtype":"${GAME_TYPE_AB}",
		"et":0,
		"introduction":"${GAME_AB_INTRO}", 
		"reward":"${GAME_AB_REWARD}", 
		"is_finish":false,
		"has_win":false,
		"winner":null}"""

		currentABSNumber = GUESS_NUMBER
		currentABBigNumber = INIT_BIG	
		currentABSmallNumber = INIT_SMALL	

		redis.set(REDIS_KEY_GAME_AB_CURRENT, currentABGid)	
		redis.set(REDIS_KEY_GAME_AB_BIG, currentABBigNumber)
		redis.set(REDIS_KEY_GAME_AB_SMALL, currentABSmallNumber)
		redis.set(REDIS_KEY_GAME_AB_START_TIME, currentABGameSt)
		redis.set(REDIS_KEY_GAME_AB_COUNT, currentABSNumber)
		
		redis.hset(REDIS_KEY_GAME_WIN_NUMBER, currentABGid, currentABWinNumber)
		redis.hset(REDIS_KEY_GAME_TOTALS, currentABGid, currentABGame)
		currentABGame
	}

	def getCurrentAllGame(uid : String) = Action {
		Ok(s"[]")
	}
	
	def getCurrentAllGames(uid : String) = Action.async {
		Logger.info(s"getCurrentAllGame uid:${uid}")

		val guessGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication.currentGuessGid).map { ee =>
			s"""{"game_info":${GameApplication.currentGuessGame},"small":${GameApplication.currentGuessSmallNumber}, 
			"big":${GameApplication.currentGuessBigNumber},"count":${ee} }"""
		}

		val abGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication1.currentABGid).map { ee =>
			s"""{"game_info":${GameApplication1.currentABGame},"small":${currentABSmallNumber}, 
			"big":${currentABBigNumber},"count":${ee},"number":${currentABSNumber} }"""
		}	

		// val lnGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication2.currentLNGid).map { ee =>
		// 	s"""{"game_info":${GameApplication2.currentLNGame},"small":${GameApplication2.currentLNSmallNumber}, 
		// 	"big":${GameApplication2.currentLNBigNumber},"count":${ee},"number":${GameApplication2.currentLNSNumber} }"""
		// }	

		for {
			a <- guessGame
			b <- abGame
			// c <- lnGame
		} yield {
			Ok(s"[${a},${b}]")	
		}
	}

	def playABGame(gid : String, uid : String, guess : String, desc : String, lid : String) = Action.async {
		Logger.info(s"current gid:${gid}, uid:${uid}, guess:${guess}, desc:${desc},  lid:${lid}")
		
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_GAME_CHECK_TIME + gid, uid).map { tt =>
			if(currentABGid != gid) {
				Logger.info(s"current gid:${currentABGid}, your gid ${gid}")
				Ok("err:1")
			} else if(isABFinish) {	
				Logger.info(s"current gid:${isABFinish} finish")
				Ok("err:2")
			} 
			else if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
				Logger.info(s"time is not over,current gid:${currentABGid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
				Ok("err:3:" + sss)
			} 
			else if(guess.split(",").length != currentABSNumber)  {
				Ok("err:4:" + currentABSNumber)
			} else {
				var aCount = 0
				val aa = currentABWinNumber.split(",").map { _.toInt }
				val bb = guess.split(",").map { _.toInt }
				(0 until currentABSNumber).foreach{ e => 
					if(aa(e) == bb(e)) aCount = aCount + 1
				}
				val bCount = bb.filter{ kk => aa.contains(kk)}.length - aCount

				if(aCount == currentABSNumber) {
					Logger.info(s"you guess the number, gid ${gid}")
					redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##" + guess + "##" + desc + "##" + currentABSNumber + "A0B")

					isABFinish = true
					redis.hset(REDIS_KEY_GAME_WINER, gid, uid)
					redis.hlen(REDIS_KEY_GAME_HISTORY + gid).map { ae =>
						currentABGame = s"""{"game_info":{"gid":"${gid}", 
									"name":"${GAME_AB_NAME}",
									"st":${currentABGameSt}, 
									"gtype":"${GAME_TYPE_AB}",
									"et":${System.currentTimeMillis},
									"introduction":"${GAME_AB_INTRO}", 
									"reward":"${GAME_AB_REWARD}", 
									"is_finish":true,
									"has_win":true,
									"total":${ae},
									"winner_n":"${guess}",
									"winner_lid":"${lid}",
									"winner":"${uid}"}}"""
						redis.hset(REDIS_KEY_GAME_AB_FINISH, gid, currentABGame)
						redis.hset(REDIS_KEY_GAME_TOTALS, gid, currentABGame)
						redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
						createABGame()						
					}

					Ok(s"""${currentABSNumber}##${0}##winner""")
				} else { 
					redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##" + guess + "##" + desc + "##" + aCount + "A" + bCount + "B")
					redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
					Logger.info(s"""current ab game currentabGid:${currentABGid},
						currentABWinNumber:${currentABWinNumber},
						currentABBigNumber:${currentABBigNumber}, 
						currentABSmallNumber:${currentABSmallNumber}, 
						isABFinish:${isABFinish},
						currentABGame:${currentABGame},
						currentABGameSt:${currentABGameSt} """)

					Ok(s"""${aCount}##${bCount}""")
				}	
			} 
		}
	}

	def reloadCurrentABGame() {
		Logger.info(s"run reloadCurrentGuessGame")
		for{
			n1 <- redis.get(REDIS_KEY_GAME_AB_BIG) // Future[Option[R]]
			n2 <- redis.get(REDIS_KEY_GAME_AB_SMALL)
			n3 <- redis.get(REDIS_KEY_GAME_AB_CURRENT)
			n4 <- redis.get(REDIS_KEY_GAME_AB_START_TIME)
			n8 <- redis.get(REDIS_KEY_GAME_AB_COUNT)
			n5 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WIN_NUMBER, n3.get.utf8String) else Future.successful(None)
			n6 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_TOTALS, n3.get.utf8String) else Future.successful(None)
			n7 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WINER, n3.get.utf8String) else Future.successful(None)
		} {
			if(n3.isDefined) currentABGid = n3.get.utf8String
			if(n5.isDefined) currentABWinNumber = n5.get.utf8String
			if(n1.isDefined) currentABBigNumber = n1.get.utf8String.toInt
			if(n2.isDefined) currentABSmallNumber = n2.get.utf8String.toInt
			if(n7.isDefined) isABFinish = true else isABFinish = false
			if(n6.isDefined) currentABGame = n6.get.utf8String
			if(n4.isDefined) currentABGameSt = n4.get.utf8String.toLong
			if(n8.isDefined) currentABSNumber = n8.get.utf8String.toInt
			Logger.info(s"""current ab game currentabGid:${currentABGid},
				currentABWinNumber:${currentABWinNumber},
				currentABBigNumber:${currentABBigNumber}, 
				currentABSmallNumber:${currentABSmallNumber}, 
				isABFinish:${isABFinish},
				currentABGame:${currentABGame},
				currentABGameSt:${currentABGameSt} """)		
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

		val ab = redis.hgetall(REDIS_KEY_GAME_AB_FINISH).map { all => //Future[Map[String, R]]
			all.map { k => 
					val jj = mapper.readTree(k._2.utf8String)
					if(jj.get("game_info").get("et") != null)
						(jj.get("game_info").get("et").asText, k._2.utf8String)
					else ("0", k._2.utf8String)
				}.toArray
		}

		// val nl = redis.hgetall(GameApplication2.REDIS_KEY_GAME_LN_FINISH).map { all => //Future[Map[String, R]]
		// 	all.map { k => 
		// 			val jj = mapper.readTree(k._2.utf8String)
		// 			if(jj.get("game_info").get("et") != null)
		// 				(jj.get("game_info").get("et").asText, k._2.utf8String)
		// 			else ("0", k._2.utf8String)
		// 		}.toArray
		// }

		for {
			aa <- guess
			bb <- ab
			// cc <- nl
		} yield {
			val ee = aa ++ bb 
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)			
		}
	}

}
