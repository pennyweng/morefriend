package controllers

import play.api._
import play.api.mvc._

import play.api.libs.iteratee._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import play.api.mvc._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.Logger

import models._

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent._

import play.api.libs.concurrent.Execution.Implicits._

import com.jookershop.morefriend.bo.ChatDataSource._
import models.Notification._

object Application extends Controller {
  var connected = Map.empty[String, Concurrent.Channel[String]]

  def getUsers = Action {
    val manCount = talkingGuid.size / 2 + waitFemale.size
    val womenCount = talkingGuid.size / 2 + waitMale.size
    Logger.debug("manCount:" + manCount + ",womenCount:" + womenCount)
    Ok(s"""{"man_count":${manCount},"women_count":${womenCount},"man_wait_count":${waitFemale.size},"women_wait_count":${waitMale.size}}""")   
  }

  def getUsersImg = Action {
    val pics = talkingUserId.keys.take(14).flatMap { uid =>
      if(yourInfoMap.contains(uid)) {
        Some("\"" + yourInfoMap(uid).picId + "\"")
      } else None
    }.mkString("[", "," , "]")
    Ok(s"""${pics}""")
  }

  def chat(userId: String) = WebSocket.async[String] { request  =>
    ChatRoom.join(userId)
  }


  def register(userId: String, gcmId : String) = Action {
      Logger.debug("register gcm => userId:" + userId + ",gcmId:" + gcmId)
      gcm += userId -> gcmId
      Ok("ok")
  }


  def setNotification(userId : String, womenCome: Boolean, menCome: Boolean, womenOver: Int, menOver: Int) = Action {
    Logger.debug("notification userId:" + userId + ",womenCome:" + womenCome + ",menCome" + menCome + ",womenOver:" + womenOver + ",menOver:" + menOver)

    if(womenCome) notifyWomenCome += userId
    if(menCome) notifyMenCome += userId

    if(womenOver != -1) {
      notifyWomenLimition += userId -> womenOver
    }

    if(menOver != -1) {
      notifyMenLimition += userId -> menOver
    }
    Ok("ok")
  }

  def resetNotification(userId : String) = Action {
    Logger.debug("resetNotification userId:" + userId)    
    notifyWomenCome -= userId
    notifyMenCome -= userId
    notifyWomenLimition -= userId
    notifyMenLimition -= userId

    Ok("ok")
  }
}