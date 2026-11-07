package pe.lgcam.control

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private val worker = Executors.newSingleThreadExecutor()
 private val previewWorker = Executors.newSingleThreadExecutor()
 private val ui = Handler(Looper.getMainLooper())
 private val base = "http://192.168.43.1:6624"
 @Volatile private var previewOn = false
 @Volatile private var previewConnection: HttpURLConnection? = null
 private lateinit var status: TextView
 private lateinit var battery: TextView
 private lateinit var preview: ImageView
 private lateinit var previewButton: Button

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.rgb(18,23,34)
  window.navigationBarColor = Color.rgb(18,23,34)
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setBackgroundColor(Color.rgb(18,23,34))
   setPadding(22,24,22,20)
  }
  val heading = TextView(this).apply {
   text = "LG 360 CAM"
   textSize = 25f
   setTextColor(Color.WHITE)
   setTypeface(null,Typeface.BOLD)
  }
  root.addView(heading)
  battery = TextView(this).apply {
   text = "LG R105 · Conecta el Wi-Fi de la cámara"
   textSize = 14f
   setTextColor(Color.LTGRAY)
   setPadding(0,8,0,18)
  }
  root.addView(battery)
  val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
  preview = ImageView(this).apply {
   scaleType = ImageView.ScaleType.FIT_CENTER
   setBackgroundColor(Color.BLACK)
  }
  frame.addView(preview, FrameLayout.LayoutParams(-1,-1))
  val placeholder = TextView(this).apply {
   text = "VISTA PREVIA 360°\nPulsa Iniciar vista previa"
   textSize = 17f
   gravity = Gravity.CENTER
   setTextColor(Color.LTGRAY)
  }
  frame.addView(placeholder, FrameLayout.LayoutParams(-1,-1))
  root.addView(frame, LinearLayout.LayoutParams(-1,0,1f))
  previewButton = Button(this).apply {
   text = "INICIAR VISTA PREVIA"
   setOnClickListener {
    if(previewOn) {
     stopPreview()
     placeholder.visibility = android.view.View.VISIBLE
    } else {
     placeholder.visibility = android.view.View.GONE
     startPreview { placeholder.visibility = android.view.View.VISIBLE }
    }
   }
  }
  root.addView(previewButton)
  fun addButton(label:String, onClick:()->Unit) {
   root.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
  }
  addButton("DETECTAR Y CONSULTAR BATERÍA") { request("GET","/osc/info",null,false); request("POST","/osc/state","{}",true) }
  val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  fun control(label:String, weight:Float, onClick:()->Unit) {
   controls.addView(Button(this).apply { text=label; setOnClickListener { onClick() } }, LinearLayout.LayoutParams(0,-2,weight))
  }
  control("FOTO",1f) { command("camera.takePicture") }
  control("GRABAR",1f) { command("camera.startCapture") }
  control("DETENER",1f) { command("camera.stopCapture") }
  root.addView(controls)
  status = TextView(this).apply {
   text = "Versión 0.2.0 · Vista previa experimental"
   setTextColor(Color.LTGRAY)
   textSize = 13f
   setTextIsSelectable(true)
   setPadding(0,12,0,8)
  }
  root.addView(status)
  setContentView(root)
  request("GET","/osc/info",null,false)
 }
 private fun command(name:String) {
  val body=JSONObject().put("name",name).toString()
  request("POST","/osc/commands/execute",body,false)
 }
 private fun request(method:String,path:String,body:String?,state:Boolean) {
  worker.execute {
   val result = try {
    val conn=(URL(base+path).openConnection() as HttpURLConnection).apply {
     requestMethod=method; connectTimeout=4000; readTimeout=7000
     if(body!=null) {
      doOutput=true
      setRequestProperty("Content-Type","application/json")
      outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
     }
    }
    val code=conn.responseCode
    val data=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
    conn.disconnect()
    if(state && code==200) {
     val level=JSONObject(data).optJSONObject("state")?.optDouble("batteryLevel",-1.0) ?: -1.0
     ui.post { battery.text=if(level>=0) "LG R105 · Batería: ${(level*100).toInt()}%" else "LG R105 · Conectada" }
    } else if(path=="/osc/info" && code==200) {
     ui.post { battery.text="LG R105 · Conectada por Wi-Fi" }
    }
    val message=try {
     val json=JSONObject(data)
     val err=json.optJSONObject("error")
     if(err!=null) "HTTP $code · ${err.optString("code")}: ${err.optString("message")}"
     else if(code==200) "HTTP 200 · ${json.optString("state","Cámara conectada")}" else "HTTP $code · $data"
    } catch(_:Exception) { "HTTP $code · $data" }
    message
   } catch(e:Exception) { "No se pudo conectar: ${e.message}" }
   ui.post { status.text=result }
  }
 }
 private fun startPreview(onUnavailable:()->Unit) {
  if(previewOn) return
  previewOn=true
  previewButton.text="DETENER VISTA PREVIA"
  status.text="Solicitando video en vivo..."
  previewWorker.execute {
   var received=false
   var failure="La cámara no ofrece vista previa con este comando."
   try {
    val conn=(URL("$base/osc/commands/execute").openConnection() as HttpURLConnection).apply {
     requestMethod="POST"; doOutput=true; connectTimeout=5000; readTimeout=12000
     setRequestProperty("Content-Type","application/json")
    }
    previewConnection=conn
    conn.outputStream.use { it.write("""{"name":"camera.getLivePreview"}""".toByteArray(Charsets.UTF_8)) }
    val code=conn.responseCode
    val type=conn.contentType ?: ""
    if(code!=200 || (!type.contains("image") && !type.contains("multipart") && !type.contains("octet-stream"))) {
     val body=conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
     failure="Vista previa no disponible (HTTP $code): ${body.take(250)}"
    } else {
     val input=conn.inputStream
     val frame=ByteArrayOutputStream()
     var previous=-1
     var inJpeg=false
     while(previewOn) {
      val b=input.read()
      if(b<0) break
      if(!inJpeg) {
       if(previous==0xff && b==0xd8) {
        frame.reset(); frame.write(0xff); frame.write(0xd8); inJpeg=true
       }
      } else {
       frame.write(b)
       if(frame.size()>3_000_000) { frame.reset(); inJpeg=false }
       else if(previous==0xff && b==0xd9) {
        val bytes=frame.toByteArray()
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        if(bitmap!=null) {
         received=true
         ui.post { if(previewOn) { preview.setImageBitmap(bitmap); status.text="Vista previa activa" } }
        }
        frame.reset(); inJpeg=false
       }
      }
      previous=b
     }
    }
    conn.disconnect()
   } catch(e:Exception) { failure="Vista previa: ${e.javaClass.simpleName}: ${e.message}" }
   finally {
    previewConnection=null
    val wasOn=previewOn
    previewOn=false
    ui.post {
     previewButton.text="INICIAR VISTA PREVIA"
     if(wasOn) {
      status.text=if(received) "Vista previa finalizada" else failure
      if(!received) onUnavailable()
     }
    }
   }
  }
 }
 private fun stopPreview() {
  previewOn=false
  previewConnection?.disconnect()
  previewButton.text="INICIAR VISTA PREVIA"
  status.text="Vista previa detenida"
 }
 override fun onDestroy() {
  stopPreview()
  worker.shutdownNow()
  previewWorker.shutdownNow()
  super.onDestroy()
 }
}
