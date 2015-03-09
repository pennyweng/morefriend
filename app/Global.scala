import play.api._

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent._
import play.api._
import play.api.mvc.Results._
import play.api.mvc._

import play.api.Play.current
import play.api.libs.concurrent._
import akka.actor.Props

import play.api.libs.concurrent.Execution.Implicits._

import models.ChatRoom._
import models._
import com.jookershop.morefriend.bo.ChatDataSource._
import models.Notification._
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._
import controllers.line.BlockApplication

object Global extends GlobalSettings {
  val GCM_FILE = "gcm.out"
  val SCHANNEL_FILE = "schnnel.out"
  val NOTIFY_WOMEN_COME_FILE = "notifyWomenCome.out"
  val NOTIFY_MEN_COME_FILE = "notifyMenCome.out"
  val NOTIFY_WOMEN_LIMITION_FILE = "notifyWomenLimition.out"
  val NOTIFY_MEN_LIMITION_FILE = "notifyMenLimition.out"
  val mapper = new ObjectMapper()
  

  override def onStart(app: Application) {
    Logger.info("Application has started")
    // controllers.line.GiftApplication2.createNewProduct
    // controllers.line.GiftQueueApplication.converQueue
    // models.Ptt.parse()
    // controllers.LineApplication.createChatRoom
    // controllers.LineApplication.postAllPost
    // models.Line.updateAllUserProfile()


    controllers.line.GameApplication.reloadCurrentGuessGame()
    controllers.line.GameApplication1.reloadCurrentABGame()
    controllers.line.GameApplication2.reloadCurrentLNGame

  	Akka.system.scheduler.schedule(0.microsecond, 10.second, default, CheckWaitingUser())
    Akka.system.scheduler.schedule(0 second, 86400 second) {
      controllers.line.BlockApplication.refreshAdminBlock()
      controllers.line.TraceApplication.delActiveHotUsers()
      // controllers.line.AdminQueue.delay()
    }

    // controllers.line.GiftApplication.genEmptyCode()

    
    // if(new java.io.File(GCM_FILE).exists) {
    // 	val src = scala.io.Source.fromFile(GCM_FILE)
    // 	src.getLines.foreach{ line => 
    // 		gcm += line.split(",")(0) -> line.split(",")(1)
    // 	} 	
    // }

    // if(new java.io.File(NOTIFY_WOMEN_COME_FILE).exists) {
    //   val src = scala.io.Source.fromFile(NOTIFY_WOMEN_COME_FILE)
    //   src.getLines.foreach{ line => 
    //     notifyWomenCome += line
    //   }   
    // }

    // if(new java.io.File(NOTIFY_MEN_COME_FILE).exists) {
    //   val src = scala.io.Source.fromFile(NOTIFY_MEN_COME_FILE)
    //   src.getLines.foreach{ line => 
    //     notifyMenCome += line
    //   }   
    // }    

    // if(new java.io.File(NOTIFY_WOMEN_LIMITION_FILE).exists) {
    //   val src = scala.io.Source.fromFile(NOTIFY_WOMEN_LIMITION_FILE)
    //   src.getLines.foreach{ line => 
    //     notifyWomenLimition += line.split(",")(0) -> line.split(",")(1).toInt
    //   }   
    // }

    // if(new java.io.File(NOTIFY_MEN_LIMITION_FILE).exists) {
    //   val src = scala.io.Source.fromFile(NOTIFY_MEN_LIMITION_FILE)
    //   src.getLines.foreach{ line => 
    //     notifyMenLimition += line.split(",")(0) -> line.split(",")(1).toInt
    //   }   
    // }

    if(new java.io.File(SCHANNEL_FILE).exists) {
      val src = scala.io.Source.fromFile(SCHANNEL_FILE)
      src.getLines.foreach{ line => 
        controllers.SChannel.leaveMsg += line.split("###")(0) -> mapper.readValue[Array[controllers.MessageInfo]](line.split("###")(1), classOf[Array[controllers.MessageInfo]])
      }   
    }
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown..., persistent gcm")
    import java.io._

	  // val pw = new PrintWriter(new BufferedWriter(new FileWriter(GCM_FILE)));
   //  gcm.foreach { gg =>
   //  	pw.println(gg._1 + "," + gg._2)
   //  }
   //  pw.flush
   //  pw.close

   //  val nw = new PrintWriter(new BufferedWriter(new FileWriter(NOTIFY_WOMEN_COME_FILE)));
   //  notifyWomenCome.toList.foreach { gg =>
   //    nw.println(gg)
   //  }
   //  nw.flush
   //  nw.close


   //  val nm = new PrintWriter(new BufferedWriter(new FileWriter(NOTIFY_MEN_COME_FILE)));
   //  notifyMenCome.toList.foreach { gg =>
   //    nm.println(gg)
   //  }
   //  nm.flush
   //  nm.close


   //  val nwl = new PrintWriter(new BufferedWriter(new FileWriter(NOTIFY_WOMEN_LIMITION_FILE)));
   //  notifyWomenLimition.foreach { gg =>
   //    nwl.println(gg._1 + "," + gg._2)
   //  }
   //  nwl.flush
   //  nwl.close

   //  val nml = new PrintWriter(new BufferedWriter(new FileWriter(NOTIFY_MEN_LIMITION_FILE)));
   //  notifyMenLimition.foreach { gg =>
   //    nml.println(gg._1 + "," + gg._2)
   //  }
   //  nml.flush
   //  nml.close    


    // val leaveMsg = new HashMap[String, Array[MessageInfo]]()
    if(controllers.SChannel.leaveMsg.size > 0 ) {
      val sf = new PrintWriter(new BufferedWriter(new FileWriter(SCHANNEL_FILE, false)));
      controllers.SChannel.leaveMsg.foreach { ee =>
        sf.println(ee._1 + "###" + mapper.writeValueAsString(ee._2))
      }
      sf.flush
      sf.close
    }


  }


  def getLatLong( address : String ) = {
    
  }

}