package pe.lgcam.control

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private val worker = Executors.newSingleThreadExecutor()
 private val ui = Handler(Looper.getMainLooper())
 private lateinit var status: TextView
 private val base = "http://192.168.43.1:6624"
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,40,32,24) }
  val title = TextView(this).apply { text = "LG 360 CAM Control"; textSize = 25f; setTypeface(null,Typeface.BOLD) }
  layout.addView(title)
  layout.addView(TextView(this).apply { text = "Conéctate primero a la red LGR105_....OSC"; textSize = 15f; setPadding(0,16,0,16) })
  status = TextView(this).apply { text = "Listo para probar"; textSize = 15f; setTextIsSelectable(true); setPadding(0,20,0,20) }
  fun button(label:String, action:()->Unit) { layout.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
  button("Detectar cámara") { request("GET","/osc/info",null) }
  button("Consultar estado") { request("POST","/osc/state", "{}") }
  button("Tomar fotografía 360°") { request("POST","/osc/commands/execute", """{"name":"camera.takePicture"}""") }
  layout.addView(status)
  val scroll=ScrollView(this); scroll.addView(layout); setContentView(scroll)
 }
 private fun request(method:String,path:String,body:String?) {
  status.text="Consultando cámara..."
  worker.execute {
   val result = try {
    val conn = (URL(base+path).openConnection() as HttpURLConnection).apply {
     requestMethod=method; connectTimeout=5000; readTimeout=10000
     if(body!=null) { doOutput=true; setRequestProperty("Content-Type","application/json"); outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
    }
    val code=conn.responseCode
    val stream=if(code in 200..299) conn.inputStream else conn.errorStream
    val response=stream?.bufferedReader()?.use { it.readText() } ?: ""
    conn.disconnect()
    "HTTP $code\n"+try { JSONObject(response).toString(2) } catch(_:Exception) { response }
   } catch(e:Exception) { "Error: ${e.javaClass.simpleName}: ${e.message}\nComprueba que sigues conectado al Wi-Fi de la LG R105." }
   ui.post { status.text=result }
  }
 }
 override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
