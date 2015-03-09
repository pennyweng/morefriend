package models

import akka.actor._
import scala.concurrent.duration._
import scala.language.postfixOps

import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import akka.util.Timeout
import akka.pattern.ask

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.Logger

import com.jookershop.morefriend.bo.MsgType._
import com.jookershop.morefriend.bo._
import com.jookershop.morefriend.bo.ChatDataSource._


case class Join(userId: String)
case class CheckWaitingUser()
case class Connected(enumerator: Enumerator[String])

object ChatRoom {
	implicit val timeout = Timeout(1 second)
  val rand = new java.util.Random()
  val jumpMax = 10
  val fixNum = 5
  val sup = "311e1b81-64c2-4da9-b2b1-c7f0a4c56ad5"
  val welcome = """歡迎大家來到這裡聊天，聊天的過程請互相尊重。聊天結束後，每個人都有機會留給對方一段話(包含聯絡方式)。最後，預祝大家都能從陌生朋友變成真正的好朋友。"""
  val MAX_TALK_WOMEN = 3
  val MAX_TALK_MEN = 1

	lazy val default = {
		val roomActor = Akka.system.actorOf(Props[ChatRoom])
		roomActor
	}

  def reSort() {

    // val waitFemaleSorted = waitFemale.toList.sortBy { _._2 } 
    val waitMaleSorted = waitMale.toList.sortBy { _._2 }    
    for(i <- 0 until waitMaleSorted.size) {
      val femaleUserGid = waitMaleSorted(i)._1
      val newNum = i + 1
      if(waitMale(femaleUserGid) != newNum) {
        waitMale(femaleUserGid) = newNum
        val femaleUserId = allGuidToUsers(femaleUserGid)
        if(connected.contains(femaleUserId)) {
          Logger.info("Send wait number " + newNum + ",femaleUserId:" + femaleUserId + " to gid " + femaleUserGid)
          
          val finfo = if( lastMaleUid != "" && yourInfoMap.contains(lastMaleUid)) {
            (yourInfoMap(lastMaleUid).nickname, yourInfoMap(lastMaleUid).picId)
          } else ("", "")

          connected(femaleUserId).push(WAIT + "#" + mapper.writeValueAsString(WaitNumber(newNum, femaleUserGid, finfo._1, finfo._2)))
        } else Logger.error("Cannot find the " + femaleUserId + " connection when re-sort")
      }
    }      

    val waitFemaleSorted = waitFemale.toList.sortBy { _._2 } 
    // val waitMaleSorted = waitMale.toList.sortBy { _._2 }    
    for(i <- 0 until waitFemaleSorted.size) {
      val maleUserGid = waitFemaleSorted(i)._1
      val newNum = i + 1
      if(waitFemale(maleUserGid) != newNum) {
        waitFemale(maleUserGid) = newNum
        val maleUserId = allGuidToUsers(maleUserGid)
        if(connected.contains(maleUserId)) {
          Logger.info("Send wait number " + newNum + " to user id " + maleUserId + ",maleUserGid:" + maleUserGid)
          
          val finfo = if(lastFemaleUid != "" && yourInfoMap.contains(lastFemaleUid)) {
            (yourInfoMap(lastFemaleUid).nickname, yourInfoMap(lastFemaleUid).picId)
          } else ("", "")

          connected(maleUserId).push(WAIT + "#" + mapper.writeValueAsString(WaitNumber(newNum, maleUserGid, finfo._1, finfo._2)))
        } else Logger.error("Cannot find the " + maleUserId + " connection when re-sort")
      }
    }
  } 


  def checkWaiting() {
    try {
      if(waitFemale.size > 0 && waitMale.size > 0) {
        val goCount = if(waitFemale.size <= waitMale.size) waitFemale.size else waitMale.size
        val waitMaleSorted = waitMale.toList.sortBy { _._2 }

        for(i <- 0 until goCount) {
          val femaleGuid = waitMaleSorted(i)._1
          val femaleUserId = if(allGuidToUsers.contains(femaleGuid)) allGuidToUsers(femaleGuid)
                             else {
                                waitMale -= femaleGuid
                                ""
                             }
          val femaleUserInfo = if(yourInfoMap.contains(femaleUserId)) yourInfoMap(femaleUserId)
                              else {
                                waitMale -= femaleGuid
                                allGuidToUsers -= femaleGuid
                                null                                  
                              }

          val waitFemaleSorted = waitFemale.toList.sortBy { _._2 }
          val newWaitMaleSorted = waitFemaleSorted.filter { maleGuid =>
            val maleUserId = allGuidToUsers(maleGuid._1)
            !talkingUserId.get(femaleUserId).getOrElse(Array[String]()).contains(maleUserId)
          }


          if(newWaitMaleSorted.size > 0 && femaleUserId != "") {
            val maleGuid = newWaitMaleSorted(0)._1
            val maleUserId = allGuidToUsers(maleGuid)
            val maleUserInfo = yourInfoMap(maleUserId)

            val fdata = Array(TALK_BEGIN + "#" + mapper.writeValueAsString(BeginTalk(femaleGuid, maleGuid, maleUserInfo)))
            if(beginTalk.contains(femaleUserId)) {
              val newUsers = beginTalk(femaleUserId) ++ fdata
              beginTalk += femaleUserId -> newUsers
            } else beginTalk += femaleUserId ->fdata

            val mdata = Array(TALK_BEGIN + "#" + mapper.writeValueAsString(BeginTalk(maleGuid, femaleGuid, femaleUserInfo)))
            if(beginTalk.contains(maleUserId)) {
              val newUsers = beginTalk(maleUserId) ++ mdata
              beginTalk += maleUserId -> newUsers
            } else beginTalk += maleUserId ->mdata

            connected(femaleUserId).push(TALK_BEGIN_N + "#e")
            connected(maleUserId).push(TALK_BEGIN_N + "#e")
            
            if(talkingUserId.contains(maleUserId)) {
              val newUsers = talkingUserId(maleUserId) ++ Array(femaleUserId)
              talkingUserId += maleUserId -> newUsers
            } else talkingUserId += maleUserId -> Array(femaleUserId)

            if(talkingUserId.contains(femaleUserId)) {
              val newUsers = talkingUserId(femaleUserId) ++ Array(maleUserId)
              talkingUserId += femaleUserId -> newUsers
            } else talkingUserId += femaleUserId -> Array(maleUserId)

            talkingGuid += femaleGuid -> maleGuid
            talkingGuid += maleGuid -> femaleGuid

            waitMale -= femaleGuid
            waitFemale -= maleGuid

            lastFemaleUid = femaleUserId
            Logger.info("match user1:" + femaleUserId + ",maleUserId:" + maleUserId )
          } else {
            // Logger.info("female user " + femaleUserId + " cannot find the match male" )
          }
        }

        if(goCount > 0) reSort()
      } else {
        // Logger.debug("Cannot match waitFemale size:" + waitFemale.size + ", wait male size:" + waitMale.size)
      }
    } catch {
      case e => e.printStackTrace
    }
    
  }

  def putWaitingQueue( userId : String, yourInfo : UserInfo) {
    if(allUsersToGuids.contains(userId)) {
      allUsersToGuids(userId).foreach { gid =>
        allGuidToUsers -= gid
        waitFemale -= gid
        waitMale -= gid
      }
      allUsersToGuids -= userId
    }

    val currentNumberWithGid : Option[(Int, String, String)] = 
      if(yourInfo.findGender == "F") { //找女生
        // if(allUsersToGuids.contains(userId)) {
          // Some(allUsersToGuids(userId).map { gid =>
          //   if(waitFemale.contains(gid))
          //   (waitFemale(gid), gid)
          // }.sortBy{ i => i._1}.apply(0))
        // } 
        // else {
        val gids = (1 to MAX_TALK_MEN).map { i =>
          val gid : String = java.util.UUID.randomUUID.toString
          allGuidToUsers += gid -> userId
          gid
        }.toArray

        allUsersToGuids += userId -> gids
        Notification.notifyMenComeDevices(yourInfo.nickname, yourInfo.picId)

        Some(gids.map { gid =>
          val yourNumber = waitFemale.size + 1
          waitFemale += gid -> yourNumber
          (yourNumber, gid, yourInfo.findGender)
        }.sortBy{ i => i._1}.apply(0))
        // }
      } else if(yourInfo.findGender == "M") { //找男生
        // if(allUsersToGuids.contains(userId)) {
        //   Some(allUsersToGuids(userId).map { gid =>
        //     (waitMale(gid), gid)
        //   }.sortBy{ i => i._1}.apply(0))
        // } else {
        val gids = (1 to MAX_TALK_WOMEN).map { i =>
          val gid = java.util.UUID.randomUUID.toString
          allGuidToUsers += gid -> userId
          gid
        }.toArray

        allUsersToGuids += userId -> gids
        lastMaleUid = userId

        Notification.notifyWomenComeDevices(yourInfo.nickname, yourInfo.picId)

        Some(gids.map { gid =>
          val yourNumber = waitMale.size + 1
          waitMale += gid -> yourNumber
          (yourNumber, gid, yourInfo.findGender)
        }.sortBy{ i => i._1}.apply(0))
        // }
      } else None

    if(currentNumberWithGid.isDefined) {
      if(connected.contains(userId)) {
        val finfo = if(currentNumberWithGid.get._3 == "F" && lastFemaleUid != "" && yourInfoMap.contains(lastFemaleUid)) { //找女生
          (yourInfoMap(lastFemaleUid).nickname, yourInfoMap(lastFemaleUid).picId)
        } else if(currentNumberWithGid.get._3 == "M" && lastMaleUid != "" && yourInfoMap.contains(lastMaleUid)) { //找
          (yourInfoMap(lastMaleUid).nickname, yourInfoMap(lastMaleUid).picId)
        } else ("", "")
        // val canjump = currentNumberWithGid.get._1.toInt > 5
        Logger.info("Send wait number " + currentNumberWithGid.get + " to user id " + userId + ",fpic id:" + finfo._2)
        connected(userId).push(WAIT + "#" + mapper.writeValueAsString(WaitNumber(currentNumberWithGid.get._1, currentNumberWithGid.get._2, finfo._1, finfo._2)))
      } else 
        Logger.error("Cannot find the " + userId + " connection")
    }    
  }


	def join(userId:String):scala.concurrent.Future[(Iteratee[String,_],Enumerator[String])] = {
		(default ? Join(userId)).map {
			case Connected(enumerator) => 
        val iteratee = Iteratee.foreach[String] { event =>
          try {
            event.split("#")(0).toInt match {
              case JOIN => 
                Logger.debug("User " + userId + " join the chat room")
                val data = event.split("#")(1)
                Logger.debug("user information :" + data)
                val yourInfo = mapper.readValue[UserInfo](data, classOf[UserInfo])
                yourInfoMap += userId -> yourInfo
                putWaitingQueue(userId, yourInfo)

              case PASS_REQ => 
                Logger.debug("PASS_REQ" + userId)
                val gid = event.split("#")(1)

                if(waitFemale.contains(gid)) {
                  val currentNumber = waitFemale(gid)
                  val jumpValue = {
                    val maxJump = if(currentNumber > fixNum) currentNumber - fixNum else 0
                    val ranJump = rand.nextInt(jumpMax) + 1
                    if(ranJump > maxJump) maxJump else ranJump
                  }
                  
                  val nextNumber = currentNumber - jumpValue
                  waitFemale.filter { vv => vv._2 >= nextNumber }.foreach { kk =>
                    waitFemale += kk._1 -> (kk._2 + 1)
                  }
                  waitFemale += gid -> nextNumber
                  connected(userId).push(PASS_RES + "#" + mapper.writeValueAsString(PassInfo(currentNumber, nextNumber, jumpValue, gid)))     
                } else if(waitMale.contains(userId)) {
                  val currentNumber = waitMale(userId)
                  val jumpValue = {
                    val maxJump = if(currentNumber > fixNum) currentNumber - fixNum else 0
                    val ranJump = rand.nextInt(jumpMax) + 1
                    if(ranJump > maxJump) maxJump else ranJump
                  }
                  
                  val nextNumber = currentNumber - jumpValue
                  waitMale.filter { vv => vv._2 >= nextNumber }.foreach { kk =>
                    waitMale += kk._1 -> (kk._2 + 1)
                  }
                  waitMale += userId -> nextNumber
                  connected(userId).push(PASS_RES + "#" + mapper.writeValueAsString(PassInfo(currentNumber, nextNumber, jumpValue, gid)))     
                }

              case TALKING => 
                Logger.debug("TALKING" + userId)
                val data = event.split("#")(1)
                val msg1 = mapper.readValue[TalkMsg](data, classOf[TalkMsg])
                Logger.info("user " + msg1.fromId + " want to talk user " + msg1.toId + " about " + msg1.msg)
                Logger.debug("connected.contains(msg1.toId):" + connected.contains(msg1.toId) 
                  + ",talkingGuid.contains(msg1.fromGid):" + talkingGuid.contains(msg1.fromGid) 
                  + ",talkingGuid(msg1.fromGid) == msg1.toGid:" + (talkingGuid.get(msg1.fromGid) == msg1.toGid)
                  + ",talkingGuid.contains(msg1.toGid) :" + talkingGuid.contains(msg1.toGid) 
                  + ",talkingGuid(msg1.toGid) == msg1.fromGid:" + (talkingGuid.get(msg1.toGid) == msg1.fromGid))

                if(connected.contains(msg1.toId) 
                  && talkingGuid.contains(msg1.fromGid)
                  && talkingGuid(msg1.fromGid) == msg1.toGid
                  && talkingGuid.contains(msg1.toGid) 
                  && talkingGuid(msg1.toGid) == msg1.fromGid) {
                  connected(msg1.toId).push(TALKING + "#" + mapper.writeValueAsString(TalkMsg(msg1.fromId, msg1.toId, msg1.msg, System.currentTimeMillis, msg1.fromGid, msg1.toGid)))
                } else {
                  Logger.debug("user " + msg1.toId + " is not vaild" )
                  val endmg = SEND_END_MSG + "#" + mapper.writeValueAsString(EndMsg(msg1.fromId, msg1.toId, "", "", 0, msg1.fromGid, msg1.toGid))
                  Logger.debug("send end msg to " + msg1.fromId + " data:" + endmg )
                  connected(msg1.fromId).push(endmg)
                  
                  // send talk end
                  talkingGuid -= msg1.fromGid
                  talkingGuid -= msg1.toGid

                  val res1 = talkingUserId.get(msg1.fromId).getOrElse(Array[String]()).filter{ e=> e != msg1.toId}
                  if(res1.size > 0) talkingUserId += msg1.fromId -> res1 else talkingUserId -=  msg1.fromId

                  val res2 = talkingUserId.get(msg1.toId).getOrElse(Array[String]()).filter{ e=> e != msg1.fromId}
                  if(res2.size > 0) talkingUserId += msg1.toId -> res2 else talkingUserId -=  msg1.toId
                }
              case TALK_BEGIN_CONFIRM =>
                Logger.debug("Receive TALK_BEGIN_CONFIRM" + userId)
                
                beginTalk.get(userId).map { rows =>
                  rows.foreach { row =>
                    Logger.debug("send msg to " +userId + " data:" + row )
                    connected(userId).push(row)
                  }
                }

                beginTalk -= userId                
                // welcome message move to another place
                //connected(femaleUserId).push(TALKING + "#" + mapper.writeValueAsString(TalkMsg(sup, "", welcome, System.currentTimeMillis)))
                //connected(maleUserId).push(TALKING + "#" + mapper.writeValueAsString(TalkMsg(sup, "", welcome, System.currentTimeMillis)))
              case TALK_END_ALL =>
                Logger.debug("TALK_END_ALL" + userId)
                talkingUserId -= userId
                
                if(allUsersToGuids.contains(userId)) {
                  allUsersToGuids(userId).foreach { gid =>
                    endTalk(userId, gid)
                    allGuidToUsers -= gid
                    waitFemale -= gid
                    waitMale -= gid
                  }
                  allUsersToGuids -= userId
                }

              case TALK_END => 
                Logger.debug("TALK_END" + userId)
                val data = event.split("#")(1)
                val endMsg = mapper.readValue[EndMsg](data, classOf[EndMsg])

                if(endMsg.toId != "") {
                  leavedMsg += endMsg.toId -> LeaveMsg(endMsg.nickname, endMsg.msg)  
                }
                
                if(endMsg.seeAgain == 1) {
                  blacklist += endMsg.fromId -> endMsg.toId
                }

                if(connected.contains(endMsg.toId) 
                  && talkingGuid.contains(endMsg.fromGid)
                  && talkingGuid(endMsg.fromGid) == endMsg.toGid
                  && talkingGuid.contains(endMsg.toGid) 
                  && talkingGuid(endMsg.toGid) == endMsg.fromGid) {

                  Logger.debug("send end msg to " + endMsg.toId + " data:" + data )
                  connected(endMsg.toId).push(SEND_END_MSG + "#" + data)
                  leavedMsg -= endMsg.toId
                } else {
                  Logger.debug("user " + endMsg.toId + " is not vaild" )
                }

                // send talk end
                talkingGuid -= endMsg.fromGid
                talkingGuid -= endMsg.toGid

                val res1 = talkingUserId.get(endMsg.fromId).getOrElse(Array[String]()).filter{ e=> e != endMsg.toId}
                if(res1.size > 0) talkingUserId += endMsg.fromId -> res1 else talkingUserId -=  endMsg.fromId

                val res2 = talkingUserId.get(endMsg.toId).getOrElse(Array[String]()).filter{ e=> e != endMsg.fromId}
                if(res2.size > 0) talkingUserId += endMsg.toId -> res2 else talkingUserId -=  endMsg.toId

              case NEXT_ONE => 
                Logger.debug("NEXT_ONE" + userId)
                val fromGid = event.split("#")(1)
                endTalk(userId, fromGid)
                
                if(yourInfoMap.contains(userId)) {
                  putWaitingQueue(userId, yourInfoMap(userId))  
                }
            }
          } catch {
            case e => e.printStackTrace
          }
        }.map { _ =>
          Logger.debug("Quit:" + userId)          
          talkingUserId -= userId

          if(allUsersToGuids.contains(userId)) {
            allUsersToGuids(userId).foreach { gid =>
              endTalk(userId, gid)
              allGuidToUsers -= gid
              waitFemale -= gid
              waitMale -= gid
            }
            allUsersToGuids -= userId
          }

          yourInfoMap -= userId
          connected -= userId
        }

        (iteratee,enumerator)
		}

	}


  def endTalk(userId : String, fromGid : String) {
    if(talkingGuid.contains(fromGid)) {
      val toGid = talkingGuid(fromGid)
      val toId = allGuidToUsers(toGid)
      Logger.debug("send SEND_END_MSG toId:" + toId + ",toGid:" + toGid + ",fromGid:" + fromGid + ",userId:" + userId)
      connected(toId).push(SEND_END_MSG + "#" + mapper.writeValueAsString(EndMsg(userId, toId, "", "", 1, fromGid, toGid)))
      talkingGuid -= toGid
      talkingGuid -= fromGid

      val res1 = talkingUserId.get(userId).getOrElse(Array[String]()).filter{ e=> e != toId}
      if(res1.size > 0) talkingUserId += userId -> res1 else talkingUserId -=  userId

      val res2 = talkingUserId.get(toId).getOrElse(Array[String]()).filter{ e=> e != userId}
      if(res2.size > 0) talkingUserId += toId -> res2 else talkingUserId -=  toId
    }

  }
}


class ChatRoom extends Actor {
  def receive = {
    case Join(userId) => {
      val e = Concurrent.unicast[String]{c =>
        Logger.info("user " + userId + " comming!")
        connected += (userId -> c)
      }
      sender ! Connected(e)
    }

    case CheckWaitingUser() =>
      ChatRoom.checkWaiting()
  }
}