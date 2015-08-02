package controllers

import play.api._
import play.api.mvc._
import play.Logger

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.JavaConversions._

import akka.actor.{ActorSystem, Props}

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._

import java.util.Calendar
import models.Ptt._
import controllers.line.TraceApplication

object PttApplication extends Controller {
	// implicit val system = Akka.system
	val mapper = new ObjectMapper()
    mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
        
    val sd = new java.text.SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
    val pageCount = 20

	def getBGBeauty( uid : String ) = Action.async {
        if(uid != "") {
            TraceApplication.updateOnline(uid)
            TraceApplication.updateActive(uid)        
        }

        for {
            bg <- redis.hgetall("PTT_BOY-GIRL_DATA")
            beauty <- redis.hgetall("PTT_BEAUTY_DATA")
            alltogether <- redis.hgetall("PTT_ALLTOGETHER_DATA")
        } yield {
            val a = bg.map { vv =>
                val s = mapper.readTree(vv._2.utf8String)
                (sd.parse(s.get("ts").asText).getTime, vv._2.utf8String)
            }.toArray

            val b = beauty.map { vv =>
                val s = mapper.readTree(vv._2.utf8String)
                (sd.parse(s.get("ts").asText).getTime, vv._2.utf8String)
            }.toArray

            val c = alltogether.map { vv =>
                val s = mapper.readTree(vv._2.utf8String)
                (sd.parse(s.get("ts").asText).getTime, vv._2.utf8String)
            }.toArray

            val rr = a ++ b ++ c
            scala.util.Sorting.quickSort(rr)
            Ok(rr.reverse.take(80).map { r => r._2 }.mkString("[", ",", "]"))
        }
	}

    def getPtt(uid : String, tp : String) = Action.async {
        if(uid != "") {
            TraceApplication.updateOnline(uid)
            TraceApplication.updateActive(uid)        
        }

        val dd = "PTT_" + tp.toUpperCase + "_DATA"

        for {
            bg <- redis.hgetall(dd)
        } yield {
            Ok(bg.map { vv =>
                val s = mapper.readTree(vv._2.utf8String)
                (sd.parse(s.get("ts").asText).getTime, vv._2.utf8String)
            }.toArray.sortBy(_._1).reverse.map { r => r._2 }.mkString("[", ",", "]"))
        }
    }  

    import scala.concurrent.Future
    val mtkKey = "MTGOOGLE"

    def getPtt1(uid : String, tp : String, page : Int) = Action.async {
        val notf = """
            {
                    "id": "https://play.google.com/store/apps/details?id=ptt.jookershop.com.readptt1",
                    "title": "[緊急事件] 請重新安裝讀PTT",
                    "subtitle": "讀PTT因為Sex板的內容違反Google政策而被下架，我們為此緊急做了修正，重新上架。建議您到play store重新安裝，以免日後新增功能無法更新，造成不便請見諒。下載位置：https://play.google.com/store/apps/details?id=ptt.jookershop.com.readptt1",
                    "author": "喬可小舖",
                    "ts": "2015-05-17T03:14:00",
                    "url": "https://play.google.com/store/apps/details?id=ptt.jookershop.com.readptt1",
                    "image": "",
                    "from": "",
                    "good": 0,
                    "normal": 0,
                    "bad": 0,
                    "body": "讀PTT因為Sex板的內容違反Google政策而被下架，我們為此緊急做了修正，重新上架。建議您到play store重新安裝，以免日後新增功能無法更新，造成不便請見諒。下載位置：https://play.google.com/store/apps/details?id=ptt.jookershop.com.readptt1",
                    "img_links": "",
                    "source_links": "",
                    "comment": []
                }
        """

        val tp1 = if(tp == "SS") "Sex" else tp

        val indexDB = "PTT_" + tp1.toUpperCase + "_INDEX"
        val dd = "PTT_" + tp1.toUpperCase + "_DATA"

        val getAids = redis.get(indexDB).map {  keys =>
            if(keys.isDefined) {
                if(page == -1) {
                    Some(keys.get.utf8String.split(",").take(MAX_ITEM))
                } else {

                    Some(keys.get.utf8String.split(",").drop(page * pageCount).take(pageCount))
                }
            } else {
                None
            } 
        }

        for {
            nt <- redis.hexists(mtkKey, uid)
            aii <- getAids //[Option[Array[String]]]
            bg <- if(aii.isDefined && aii.get.size > 0) redis.hmget(dd, aii.get : _*) else Future.successful(List()) //Future[Seq[Option[R]]]
        } yield {
            val ss1 = bg.flatten.map { vv =>
                val s = mapper.readTree(vv.utf8String)
                (sd.parse(s.get("ts").asText).getTime, vv.utf8String)
            }.toArray.sortBy(_._1).reverse.map { r => r._2 }

            val extra = if(nt) ss1 else {
                redis.hset(mtkKey, uid, "ok")
                Array(notf) ++ ss1
            } 
            Ok(extra.mkString("[", ",", "]"))
        }
    } 

    def getPtt2(uid : String, tp : String, page : Int) = Action.async {
        val tp1 = if(tp == "SS") "Sex" else tp

        val indexDB = "PTT_" + tp1.toUpperCase + "_INDEX"
        val dd = "PTT_" + tp1.toUpperCase + "_DATA"

        val getAids = redis.get(indexDB).map {  keys =>
            if(keys.isDefined) {
                if(page == -1) {
                    Some(keys.get.utf8String.split(",").take(MAX_ITEM))
                } else {

                    Some(keys.get.utf8String.split(",").drop(page * pageCount).take(pageCount))
                }
            } else {
                None
            } 
        }

        for {
            aii <- getAids //[Option[Array[String]]]
            bg <- if(aii.isDefined && aii.get.size > 0) redis.hmget(dd, aii.get : _*) else Future.successful(List()) //Future[Seq[Option[R]]]
        } yield {
            Ok(bg.flatten.map { vv =>
                val s = mapper.readTree(vv.utf8String)
                (sd.parse(s.get("ts").asText).getTime, vv.utf8String)
            }.toArray.sortBy(_._1).reverse.map { r => r._2 }.mkString("[", ",", "]"))
        }
    } 

    val pttlist = """[{"n":"Gossiping","agr":1},{"n":"NBA","agr":0},{"n":"LoL","agr":0},{"n":"WomenTalk","agr":0},{"n":"Baseball","agr":0},{"n":"PuzzleDragon","agr":0},{"n":"movie","agr":0},{"n":"C_Chat","agr":0},{"n":"Stock","agr":0},{"n":"Boy-Girl","agr":0},{"n":"ToS","agr":0},{"n":"joke","agr":0},{"n":"Tech_Job","agr":0},{"n":"MobileComm","agr":0},
{"n":"Japan_Travel","agr":0},{"n":"BabyMother","agr":0},{"n":"KR_Entertain","agr":0},
{"n":"marvel","agr":0},{"n":"Beauty","agr":0},{"n":"BuyTogether","agr":0},{"n":"NBA_Film","agr":0},
{"n":"StupidClown","agr":0},{"n":"Hearthstone","agr":0},{"n":"DIABLO","agr":0},{"n":"marriage","agr":0},
{"n":"car","agr":0},{"n":"gay","agr":0},{"n":"AllTogether","agr":0},{"n":"e-shopping","agr":0},{"n":"Tainan","agr":0},
{"n":"MakeUp","agr":0},{"n":"KanColle","agr":0},{"n":"SportLottery","agr":1},{"n":"HatePolitics","agr":0},
{"n":"Elephants","agr":0},{"n":"Lifeismoney","agr":0},{"n":"Japandrama","agr":0},{"n":"home-sale","agr":0},
{"n":"MLB","agr":0},{"n":"Sex","agr":1},{"n":"GetMarry","agr":0},{"n":"KoreaDrama","agr":0},{"n":"PlayStation","agr":0},
{"n":"EAseries","agr":0},{"n":"Salary","agr":0},{"n":"Wanted","agr":0},{"n":"iPhone","agr":0},
{"n":"FCBarcelona","agr":0},{"n":"Food","agr":0},{"n":"PC_Shopping","agr":0},{"n":"BeautySalon","agr":0},
{"n":"MenTalk","agr":0},{"n":"Steam","agr":0},{"n":"Aviation","agr":0},
{"n":"studyteacher","agr":0},{"n":"MuscleBeach","agr":0},{"n":"creditcard","agr":0},{"n":"TaichungBun","agr":0},
{"n":"mobilesales","agr":0},{"n":"Hsinchu","agr":0},{"n":"lesbian","agr":0},{"n":"Examination","agr":0}
]"""


    val pttlist1 = """[{"n":"Gossiping","agr":1},{"n":"NBA","agr":0},{"n":"LoL","agr":0},{"n":"WomenTalk","agr":0},{"n":"Baseball","agr":0},{"n":"PuzzleDragon","agr":0},{"n":"movie","agr":0},{"n":"C_Chat","agr":0},{"n":"Stock","agr":0},{"n":"Boy-Girl","agr":0},{"n":"ToS","agr":0},{"n":"joke","agr":0},{"n":"Tech_Job","agr":0},{"n":"MobileComm","agr":0},
{"n":"Japan_Travel","agr":0},{"n":"BabyMother","agr":0},{"n":"KR_Entertain","agr":0},
{"n":"marvel","agr":0},{"n":"Beauty","agr":0},{"n":"BuyTogether","agr":0},{"n":"NBA_Film","agr":0},
{"n":"StupidClown","agr":0},{"n":"Hearthstone","agr":0},{"n":"DIABLO","agr":0},{"n":"marriage","agr":0},
{"n":"car","agr":0},{"n":"gay","agr":0},{"n":"AllTogether","agr":0},{"n":"e-shopping","agr":0},{"n":"Tainan","agr":0},
{"n":"MakeUp","agr":0},{"n":"KanColle","agr":0},{"n":"SportLottery","agr":1},{"n":"HatePolitics","agr":0},
{"n":"Elephants","agr":0},{"n":"Lifeismoney","agr":0},{"n":"Japandrama","agr":0},{"n":"home-sale","agr":0},
{"n":"MLB","agr":0},{"n":"GetMarry","agr":0},{"n":"KoreaDrama","agr":0},{"n":"PlayStation","agr":0},
{"n":"EAseries","agr":0},{"n":"Salary","agr":0},{"n":"Wanted","agr":0},{"n":"iPhone","agr":0},
{"n":"FCBarcelona","agr":0},{"n":"Food","agr":0},{"n":"PC_Shopping","agr":0},{"n":"BeautySalon","agr":0},
{"n":"MenTalk","agr":0},{"n":"Steam","agr":0},{"n":"Aviation","agr":0},
{"n":"studyteacher","agr":0},{"n":"MuscleBeach","agr":0},{"n":"creditcard","agr":0},{"n":"TaichungBun","agr":0},
{"n":"mobilesales","agr":0},{"n":"Hsinchu","agr":0},{"n":"lesbian","agr":0},{"n":"Examination","agr":0}
]"""

    val pttlist2 = """[{"n":"Gossiping","agr":1},{"n":"NBA","agr":0},{"n":"LoL","agr":0},{"n":"WomenTalk","agr":0},{"n":"Baseball","agr":0},{"n":"PuzzleDragon","agr":0},{"n":"movie","agr":0},{"n":"C_Chat","agr":0},{"n":"Stock","agr":0},{"n":"Boy-Girl","agr":0},{"n":"ToS","agr":0},{"n":"joke","agr":0},{"n":"Tech_Job","agr":0},{"n":"MobileComm","agr":0},
{"n":"Japan_Travel","agr":0},{"n":"BabyMother","agr":0},{"n":"KR_Entertain","agr":0},
{"n":"marvel","agr":0},{"n":"Beauty","agr":0},{"n":"BuyTogether","agr":0},{"n":"NBA_Film","agr":0},
{"n":"StupidClown","agr":0},{"n":"Hearthstone","agr":0},{"n":"DIABLO","agr":0},{"n":"marriage","agr":0},
{"n":"car","agr":0},{"n":"gay","agr":0},{"n":"AllTogether","agr":0},{"n":"e-shopping","agr":0},{"n":"Tainan","agr":0},
{"n":"MakeUp","agr":0},{"n":"KanColle","agr":0},{"n":"SportLottery","agr":1},{"n":"HatePolitics","agr":0},
{"n":"Elephants","agr":0},{"n":"Lifeismoney","agr":0},{"n":"Japandrama","agr":0},{"n":"home-sale","agr":0},
{"n":"MLB","agr":0},{"n":"GetMarry","agr":0},{"n":"KoreaDrama","agr":0},{"n":"PlayStation","agr":0},
{"n":"EAseries","agr":0},{"n":"Salary","agr":0},{"n":"Wanted","agr":0},{"n":"iPhone","agr":0},
{"n":"FCBarcelona","agr":0},{"n":"Food","agr":0},{"n":"PC_Shopping","agr":0},{"n":"BeautySalon","agr":0},
{"n":"MenTalk","agr":0},{"n":"Steam","agr":0},{"n":"Aviation","agr":0},
{"n":"studyteacher","agr":0},{"n":"MuscleBeach","agr":0},{"n":"creditcard","agr":0},{"n":"TaichungBun","agr":0},
{"n":"mobilesales","agr":0},{"n":"Hsinchu","agr":0},{"n":"lesbian","agr":0},{"n":"Examination","agr":0}
]"""

    def getAllList() = Action {
        Ok(pttlist)
        // redis.get("PTT_LIST").map { ll =>
        //     if(ll.isDefined) Ok(ll.get.utf8String)
        //     else Ok(pttlist)
        // }
    }  

    def getAllList1() = Action {
        Ok(pttlist1)
    }  

    def getAllList2() = Action {
        Ok(pttlist2)
    }           
}
