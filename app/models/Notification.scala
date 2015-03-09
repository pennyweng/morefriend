package models

import scala.collection.mutable.Map
import scala.collection.mutable.MutableList
import scala.collection.immutable.List
import scala.collection.JavaConversions._
import play.Logger
import com.google.android.gcm.server._

import com.jookershop.morefriend.bo.ChatDataSource._
import scala.collection.mutable.ListBuffer

object Notification {
	val SENDER_ID = "AIzaSyA1E1iw9Mq5nQkqJenoZbEZPFl-eo7bNfQ"

	val gcm = Map.empty[String, String]

	val notifyWomenCome = ListBuffer[String]()

	val notifyMenCome = ListBuffer[String]()  

	val notifyWomenLimition = Map.empty[String, Int] 

	val notifyMenLimition = Map.empty[String, Int]   

	val notifyLastTime = Map.empty[String, Long]


	def sendLineFriend(androidTargets : List[String], userMessage : String) {
		send(androidTargets, userMessage, "分類交友")
	}

	def send(androidTargets : List[String], userMessage : String) {
		send(androidTargets, userMessage, "morefriend")
	}

	def send(androidTargets : List[String], userMessage : String, keyName : String) {
		val sender = new Sender(SENDER_ID);
		val message = new Message.Builder()
		.collapseKey(keyName)
		.timeToLive(60)
		.delayWhileIdle(false)
		.addData("message", userMessage)
		.build();
		 
		try {
		    val result = sender.send(message, androidTargets, 1);
		     
		    if (result.getResults() != null) {
		        val canonicalRegId = result.getCanonicalIds();
		        if (canonicalRegId != 0) {
		        	Logger.debug("broadcase is success, canonicalRegId" + canonicalRegId)
		        }
		    } else {
		        val error = result.getFailure();
		        Logger.debug("Broadcast failure: " + error)
		    }
		    Logger.debug("notify finish result :" + result.toString) 
		} catch {
			case e : Exception =>
		    	e.printStackTrace();
		}		
	}

	def notifyWomenComeDevices(name: String, url : String) {
		val now = System.currentTimeMillis
		val targetDevices = notifyWomenCome.filter{ uid => gcm.contains(uid) && (!notifyLastTime.contains(uid) || ( notifyLastTime.contains(uid) && now - notifyLastTime(uid) > 3600000l))}.map {
			uid => 
				val gcmId = gcm(uid)
				notifyLastTime += uid -> now
				gcmId
		}.distinct.toList

		if(targetDevices.length > 0) {
			Logger.info("gcm notifyWomenComeDevices notify " + targetDevices.length + "devices!")
			send(targetDevices, "女生" + name + "想聊天喔! 快點加入吧!!!##" + url)
		}
		// checkOverWomen()
	}

	def checkOverWomen() {
		val now = System.currentTimeMillis
		val currentWomen = talkingGuid.size / 2
		val targetDevices = notifyWomenLimition.filter{ k => k._2 >= currentWomen && gcm.contains(k._1) && (!notifyLastTime.contains(k._1) || ( notifyLastTime.contains(k._1) && now - notifyLastTime(k._1) > 3600000l)) }.map {
			v => 
				val uid = v._1
				val gcmId = gcm(uid)
				notifyLastTime += uid -> now
				gcmId
		}.toList.distinct.toList

		if(targetDevices.length > 0) {
			Logger.info("gcm checkOverWomen notify " + targetDevices.length + "devices!")
			send(targetDevices, "線上女生已經超過你的設定! 快點上來聊天吧!")
		}
	}

	def checkOverMen() {
		val now = System.currentTimeMillis
		val currentMen = talkingGuid.size / 2
		val targetDevices = notifyMenLimition.filter{ k => k._2 >= currentMen && gcm.contains(k._1) && (!notifyLastTime.contains(k._1) || ( notifyLastTime.contains(k._1) && now - notifyLastTime(k._1) > 3600000l)) }.map {
			v => 
				val uid = v._1
				val gcmId = gcm(uid)
				notifyLastTime += uid -> now
				gcmId
		}.toList.distinct.toList

		if(targetDevices.length > 0) {
			Logger.info("gcm checkOverMen notify " + targetDevices.length + "devices!")
			send(targetDevices, "線上男生已經超過你的設定! 快點上來聊天吧!")
		}
	}

	def notifyMenComeDevices(name: String, url : String) {
		val now = System.currentTimeMillis
		val targetDevices = notifyMenCome.filter{ uid => gcm.contains(uid) && (!notifyLastTime.contains(uid) || ( notifyLastTime.contains(uid) && now - notifyLastTime(uid) > 3600000l))}.map {
			uid => 
				val gcmId = gcm(uid)
				notifyLastTime += uid -> now
				gcmId
		}.distinct.toList

		if(targetDevices.length > 0) {
			Logger.info("gcm notifyMenComeDevices notify " + targetDevices.length + "devices!")
			send(targetDevices, "男生" + name + "想聊天喔! 快點加入吧!!!##" + url)
		}
		// checkOverMen()
	}	
}