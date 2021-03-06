/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.apis.app

import android.app.{Notification, NotificationManager, PendingIntent, Service}
import android.app.Service._
import android.content.Intent
import android.content.Context._
import android.os.{Bundle, Handler, HandlerThread, IBinder, Looper, Message, Process}
import android.util.Log
import android.widget.Toast

import com.example.android.apis.R

/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application.  The {@link ServiceStartArgumentsController}
 * class shows how to interact with the service. 
 *
 * <p>Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 * 
 * <p>For applications targeting Android 1.5 or beyond, you may want consider
 * using the android.app.IntentService class, which takes care of all the
 * work of creating the extra thread and dispatching commands to it.
 */
class ServiceStartArguments extends Service {
  private var mNM: NotificationManager = _
  private var mInvokeIntent: Intent = _
  @volatile
  private var mServiceLooper: Looper = _
  @volatile
  private var mServiceHandler: ServiceHandler = _
    
  private final class ServiceHandler(looper: Looper) extends Handler(looper) {
        
    override def handleMessage(msg: Message) {
      val arguments = msg.obj.asInstanceOf[Bundle]
      var txt = arguments getString "name"
            
      Log.i("ServiceStartArguments", "Message: " + msg + ", "
            + arguments.getString("name"))
        
      txt = if ((msg.arg2&Service.START_FLAG_REDELIVERY) == 0)
        "New cmd #" + msg.arg1 + ": " + txt
      else
        "Re-delivered #" + msg.arg1 + ": " + txt

      showNotification(txt)

      // Normally we would do some work here...  for our sample, we will
      // just sleep for 5 seconds.
      val endTime = System.currentTimeMillis + 5*1000
      while (System.currentTimeMillis < endTime) {
        this synchronized {
          try {
            wait(endTime - System.currentTimeMillis)
          } catch {
            case e: Exception =>
          }
        }
      }

      hideNotification()

      Log.i("ServiceStartArguments", "Done with #" + msg.arg1)
      stopSelf(msg.arg1)
    }

  }
    
  override def onCreate() {
    mNM = getSystemService(NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

    Toast.makeText(this, R.string.service_created, Toast.LENGTH_SHORT).show()
        
    // This is who should be launched if the user selects our persistent
    // notification.
    mInvokeIntent = new Intent(this, classOf[ServiceStartArgumentsController])

    // Start up the thread running the service.  Note that we create a
    // separate thread because the service normally runs in the process's
    // main thread, which we don't want to block.  We also make it
    // background priority so CPU-intensive work will not disrupt our UI.
    val thread = new HandlerThread("ServiceStartArguments",
                                   Process.THREAD_PRIORITY_BACKGROUND)
    thread.start()
        
    mServiceLooper = thread.getLooper
    mServiceHandler = new ServiceHandler(mServiceLooper)
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    Log.i("ServiceStartArguments",
          "Starting #" + startId + ": " + intent.getExtras)
    val msg = mServiceHandler.obtainMessage()
    msg.arg1 = startId
    msg.arg2 = flags
    msg.obj = intent.getExtras
    mServiceHandler.sendMessage(msg)
    Log.i("ServiceStartArguments", "Sending: " + msg)
        
    // For the start fail button, we will simulate the process dying
    // for some reason in onStartCommand().
    if (intent.getBooleanExtra("fail", false)) {
      // Don't do this if we are in a retry... the system will
      // eventually give up if we keep crashing.
      if ((flags&START_FLAG_RETRY) == 0) {
        // Since the process hasn't finished handling the command,
        // it will be restarted with the command again, regardless of
        // whether we return START_REDELIVER_INTENT.
        Process.killProcess(Process.myPid())
      }
    }
        
    // Normally we would consistently return one kind of result...
    // however, here we will select between these two, so you can see
    // how they impact the behavior.  Try killing the process while it
    // is in the middle of executing the different commands.
    if (intent.getBooleanExtra("redeliver", false))
      START_REDELIVER_INTENT
    else
      START_NOT_STICKY
  }

  override def onDestroy() {
    mServiceLooper.quit()

    hideNotification()

    // Tell the user we stopped.
    Toast.makeText(ServiceStartArguments.this, R.string.service_destroyed,
                   Toast.LENGTH_SHORT).show()
  }

  override def onBind(intent: Intent): IBinder = {
    null
  }

  /**
   * Show a notification while this service is running.
   */
  private def showNotification(text: String) {
    // Set the icon, scrolling text and timestamp
    val notification = new Notification(R.drawable.stat_sample, text,
                                        System.currentTimeMillis)

    // The PendingIntent to launch our activity if the user selects this notification
    val contentIntent = PendingIntent.getActivity(this, 0,
                        new Intent(this, classOf[AlarmService]), 0)

    // Set the info for the views that show in the notification panel.
    notification.setLatestEventInfo(this,
                       getText(R.string.service_start_arguments_label),
                       text, contentIntent)

    // We show this for as long as our service is processing a command.
    notification.flags |= Notification.FLAG_ONGOING_EVENT
        
    // Send the notification.
    // We use a string id because it is a unique number.  We use it later to cancel.
    mNM.notify(R.string.service_created, notification)
  }
    
  private def hideNotification() {
    mNM cancel R.string.service_created
  }
}

