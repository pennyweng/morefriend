package controllers

import play.api._
import play.api.mvc._

import java.security.MessageDigest

import scala.collection.mutable.HashMap

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._


/**************************************************************************************************************/
/**************************************************************************************************************/
/**************************************************************************************************************/
/*************************************悄悄話*******************************************************************/
/**************************************************************************************************************/
/**************************************************************************************************************/
/**************************************************************************************************************/


case class MessageInfo @JsonCreator() (
	@scala.reflect.BeanProperty @JsonProperty("msg") msg : String, 
	@scala.reflect.BeanProperty @JsonProperty("ts") ts : Long,
	@scala.reflect.BeanProperty @JsonProperty("uid") uid : String,
	@scala.reflect.BeanProperty @JsonProperty("room") room : String,
	@scala.reflect.BeanProperty @JsonProperty("img") img : String = ""
	)


object SChannel extends Controller {
	val mapper = new ObjectMapper()
	val leaveMsg = new HashMap[String, Array[MessageInfo]]()
	val uploadPath = "./schannel"

	def index() = Action {
		Ok(views.html.index(Array[MessageInfo](), ""))
	}

	def talkhtml( msg : String, uid : String) = Action { request =>
		Logger.debug("msg:" + msg + ", uid:" + uid)
		if(vaildMsg(msg)) {
			if(isLeaveMsg(msg)) {
				val rooms = msg.substring(0, msg.indexOf(":"))
				val msgValue = msg.substring(msg.indexOf(":") + 1)

				rooms.split("@").filter{ e=> e.trim.length > 0}.map{ e=> e.trim}.flatMap { room =>
					val key = md5base64(room)
					val newmsg = Array(MessageInfo(msgValue, System.currentTimeMillis, uid, "@" + room))
					if(leaveMsg.contains(key)) {
						leaveMsg += key -> (leaveMsg(key) ++ newmsg)
					} else {
						leaveMsg += key -> newmsg
					}
				}
				
				Ok(views.html.index(Array[MessageInfo](), "傳送成功"))
				// Ok("""{"ret":[],"status":1}""")
			} else {
				val resp = msg.split("@").filter{ e=> e.trim.length > 0}.map{ e=> e.trim}.flatMap { dd =>
					val key = md5base64(dd)
					val msgInfos = leaveMsg.get(key).getOrElse(Array[MessageInfo]())
					leaveMsg += key -> msgInfos.filter{ msgInfo => msgInfo.uid == uid }.toArray
					// msgInfos.filter{ msgInfo => msgInfo.uid != uid && msgInfo.img != ""}.map { mi =>
					// 	val path = uploadPath + "/" + mi.img + ".jpg"
					// 	new java.io.File(path).delete
					// }
					
					msgInfos
				}

				val resMsg = if(resp.size == 0) "沒有任何悄悄話" else ""
				Ok(views.html.index(resp, resMsg))
				// val ret = mapper.writeValueAsString(resp)
				// Ok(s"""{"ret":${ret},"status":2}""")
			}
		} else Ok(views.html.index(Array[MessageInfo](), "格式有誤喔!!!"))// Ok("""{"ret":[],"status":-1}""")
	}


	def talk( msg : String, uid : String) = Action {
		Logger.debug("msg:" + msg + ", uid:" + uid)
		if(vaildMsg(msg)) {
			if(isLeaveMsg(msg)) {
				val rooms = msg.substring(0, msg.indexOf(":"))
				val msgValue = msg.substring(msg.indexOf(":") + 1)

				Logger.debug(s"rooms:${rooms}, msg:${msgValue}")
				rooms.split("@").filter{ e=> e.trim.length > 0}.map{ e=> e.trim}.flatMap { room =>
					val key = md5base64(room)
					val newmsg = Array(MessageInfo(msgValue, System.currentTimeMillis, uid, "@" + room))
					if(leaveMsg.contains(key)) {
						leaveMsg += key -> (leaveMsg(key) ++ newmsg)
					} else {
						leaveMsg += key -> newmsg
					}
				}
				
				Ok("""{"ret":[],"status":1}""")
			} else {
				val resp = msg.split("@").filter{ e=> e.trim.length > 0}.map{ e=> e.trim}.flatMap { dd =>
					val key = md5base64(dd)
					val msgInfos = leaveMsg.get(key).getOrElse(Array[MessageInfo]())
					leaveMsg += key -> msgInfos.filter{ msgInfo => msgInfo.uid == uid }.toArray	
					// msgInfos.filter{ msgInfo => msgInfo.uid != uid && msgInfo.img != ""}.map { mi =>
					// 	val path = uploadPath + "/" + mi.img + ".jpg"
					// 	new java.io.File(path).delete
					// }
					msgInfos
				}

				val ret = mapper.writeValueAsString(resp)
				Ok(s"""{"ret":${ret},"status":2}""")
			}
		} else Ok("""{"ret":[],"status":-1}""")
	}

	def vaildMsg( msg : String) = {
		msg.indexOf("@") != -1
	}

	def isLeaveMsg( msg : String) = {
		msg.indexOf(":") != -1
	}

	def md5base64(s: String) = { 
		new sun.misc.BASE64Encoder().encode(MessageDigest.getInstance("MD5").digest(s.getBytes))
	}


	def getImg(id : String) = Action {
		Logger.info(s"get image ${id}")
		val path = uploadPath + "/" + id + ".jpg"
  		Ok.sendFile(new java.io.File(path))
	}	

	def uploadImage(msg : String, uid : String) = Action(parse.temporaryFile) { request =>
		Logger.info( s"uploadImage uid:${uid}")

		if(vaildMsg(msg) && isLeaveMsg(msg)) {
			val rooms = msg.substring(0, msg.indexOf(":"))
			val msgValue = msg.substring(msg.indexOf(":") + 1).replaceAll("img", "")

			// rooms.split("@").filter{ e=> e.trim.length > 0}.map{ e=> e.trim}.flatMap { room =>
				val room = rooms.replaceAll("@","").trim
				val ll = room + System.currentTimeMillis
				val path = uploadPath + "/" + ll + ".jpg"
				Logger.info(" file upload path " + path)
		  		request.body.moveTo(new java.io.File(path), true)

				val key = md5base64(room)
				val newmsg = Array(MessageInfo(msgValue, System.currentTimeMillis, uid, "@" + room, ll))
				if(leaveMsg.contains(key)) {
					leaveMsg += key -> (leaveMsg(key) ++ newmsg)
				} else {
					leaveMsg += key -> newmsg
				}
			// }			
		  	Ok("""{"ret":[],"status":1}""")
		} else Ok("""{"ret":[],"status":-1}""")
	}
}