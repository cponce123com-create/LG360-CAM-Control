package pe.lgcam.control

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
 private lateinit var recordingInfo: TextView
 private var recordingRequested = false

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
  root.addView(Button(this).apply { text="CALIBRAR VISTA (ALINEACIÓN VISUAL)"; setOnClickListener { showCalibration() } })
  root.addView(Button(this).apply { text="ACTIVAR PREVIA LG (EXPERIMENTAL)"; setOnClickListener { command("camera._startPreview") } })
  root.addView(Button(this).apply { text="DESACTIVAR PREVIA LG"; setOnClickListener { stopPreview(); command("camera._stopPreview") } })
  fun addButton(label:String, onClick:()->Unit) {
   root.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
  }
  addButton("DETECTAR Y CONSULTAR BATERÍA") { request("GET","/osc/info",null,false); request("POST","/osc/state","{}",true) }
  recordingInfo=TextView(this).apply { text="Grabación: sin iniciar · Comprueba la microSD"; setTextColor(Color.WHITE); textSize=15f; setPadding(0,10,0,10) }
  root.addView(recordingInfo)
  root.addView(Button(this).apply { text="GALERÍA SD · VER FOTOS Y VIDEOS"; setOnClickListener { openGallery() } })
  val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  fun control(label:String, weight:Float, onClick:()->Unit) {
   controls.addView(Button(this).apply { text=label; setOnClickListener { onClick() } }, LinearLayout.LayoutParams(0,-2,weight))
  }
  control("FOTO",1f) { command("camera.takePicture") }
  control("GRABAR",1f) { recordVideo(true) }
  control("DETENER",1f) { recordVideo(false) }
  root.addView(controls)
  status = TextView(this).apply {
   text = "Versión 0.6.0 · Descargas y ajuste visual"
   setTextColor(Color.LTGRAY)
   textSize = 13f
   setTextIsSelectable(true)
   setPadding(0,12,0,8)
  }
  root.addView(status)
  setContentView(root)
  request("GET","/osc/info",null,false)
 }
 private fun openGallery() {
  status.text="Consultando archivos de la microSD..."
  worker.execute {
   try {
    val params=JSONObject().put("fileType","all").put("entryCount",100).put("startPosition",0)
    val result=postJson("/osc/commands/execute",JSONObject().put("name","camera.listFiles").put("parameters",params).toString())
    val entries=JSONObject(result.second).optJSONObject("results")?.optJSONArray("entries")
    ui.post {
     if(result.first!=200 || entries==null) {
      status.text="Galería SD · HTTP ${result.first} · ${result.second.take(450)}"
     } else {
      val labels=ArrayList<String>()
      val urls=ArrayList<String>()
      for(i in 0 until entries.length()) {
       val item=entries.optJSONObject(i) ?: continue
       labels.add(item.optString("name","Archivo ${i+1}"))
       urls.add(item.optString("fileUrl"))
      }
      if(labels.isEmpty()) { status.text="La cámara no devolvió archivos"; return@post }
      android.app.AlertDialog.Builder(this)
       .setTitle("Galería SD · ${labels.size} archivos")
       .setItems(labels.toTypedArray()) { _,index -> showFileActions(labels[index],urls[index]) }
       .setPositiveButton("Cerrar",null).show()
      status.text="Galería SD · ${labels.size} archivos encontrados"
     }
    }
   } catch(e:Exception) { ui.post { status.text="Error al consultar galería: ${e.message}" } }
  }
 }
 private fun showCalibration() {
  val container=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(30,18,30,8) }
  val explanation=TextView(this).apply {
   text="Ajuste visual de rotación de la vista previa. No modifica las lentes ni calibra el cosido 360°."
  }
  container.addView(explanation)
  val angle=TextView(this).apply { text="Rotación: ${preview.rotation.toInt()}°" }
  container.addView(angle)
  val seek=SeekBar(this).apply { max=360; progress=((preview.rotation.toInt()%360)+360)%360 }
  seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
    if(fromUser) { preview.rotation=value.toFloat(); angle.text="Rotación: $value°" }
   }
   override fun onStartTrackingTouch(bar:SeekBar?) {}
   override fun onStopTrackingTouch(bar:SeekBar?) {}
  })
  container.addView(seek)
  android.app.AlertDialog.Builder(this).setTitle("Calibración visual experimental")
   .setView(container).setNeutralButton("Restablecer") { _,_ -> preview.rotation=0f }
   .setPositiveButton("Listo",null).show()
 }
 private fun cameraUri(raw:String):Uri? {
  val candidate=if(raw.startsWith("/")) "$base$raw" else raw
  val uri=try { Uri.parse(candidate) } catch(_:Exception) { return null }
  if(uri.scheme!="http" || uri.host!="192.168.43.1" || (uri.port!=-1 && uri.port!=6624)) return null
  return uri.buildUpon().encodedPath(Uri.encode(uri.path ?: "/", "/")).build()
 }
 private fun showFileActions(name:String,raw:String) {
  android.app.AlertDialog.Builder(this).setTitle(name)
   .setItems(arrayOf("Ver archivo","Descargar al teléfono")) { _,choice ->
    if(choice==0) viewCameraFile(name,raw) else downloadCameraFile(name,raw)
   }.setNegativeButton("Cancelar",null).show()
 }
 private fun downloadCameraFile(name:String,raw:String) {
  val uri=cameraUri(raw)
  if(uri==null) { status.text="Dirección de descarga no válida"; return }
  val safeName=name.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._ -]"),"_")
   .take(120).ifBlank { "LG360_media" }
  status.text="Preparando descarga directa: $safeName"
  worker.execute {
   var conn:HttpURLConnection?=null
   var destination:Uri?=null
   var legacyFile:File?=null
   var complete=false
   try {
    val manager=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifi=manager.allNetworks.firstOrNull { network ->
     manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
    } ?: throw IllegalStateException("Conecta primero al Wi-Fi de la LG R105")
    conn=(wifi.openConnection(URL(uri.toString())) as HttpURLConnection).apply {
     requestMethod="GET"; connectTimeout=8000; readTimeout=20000
     instanceFollowRedirects=false
    }
    val code=conn.responseCode
    if(code!=200 && code!=206) throw IllegalStateException("La cámara respondió HTTP $code")
    val expected=conn.contentLengthLong
    if(Build.VERSION.SDK_INT>=29) {
     val values=ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME,safeName)
      put(MediaStore.Downloads.MIME_TYPE,if(safeName.endsWith(".mp4",true)) "video/mp4" else "application/octet-stream")
      put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/LG360CAM")
      put(MediaStore.Downloads.IS_PENDING,1)
     }
     destination=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
      ?: throw IllegalStateException("No se pudo crear el archivo de destino")
    } else {
     val folder=File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"LG360CAM")
     if(!folder.exists() && !folder.mkdirs()) throw IllegalStateException("No se pudo crear carpeta")
     legacyFile=File(folder,safeName)
    }
    val output=if(destination!=null) contentResolver.openOutputStream(destination!!)
     ?: throw IllegalStateException("No se pudo abrir el destino")
     else FileOutputStream(legacyFile!!)
    var received=0L
    var lastUpdate=0L
    output.use { out ->
     conn!!.inputStream.use { input ->
      val buffer=ByteArray(65536)
      while(true) {
       val n=input.read(buffer)
       if(n<0) break
       out.write(buffer,0,n)
       received+=n
       if(received-lastUpdate>=512*1024) {
        lastUpdate=received
        val done=received
        ui.post { status.text=if(expected>0) "Descargando $safeName · ${done*100/expected}%"
         else "Descargando $safeName · ${done/1048576} MB" }
       }
      }
     }
    }
    if(received==0L || (expected>0 && received!=expected)) throw IllegalStateException("Transferencia incompleta: $received de $expected bytes")
    if(destination!=null && Build.VERSION.SDK_INT>=29) {
     contentResolver.update(destination!!,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null)
    }
    complete=true
    val total=received
    ui.post { status.text="Descarga completada: $safeName · ${total/1024} KB · Descargas/LG360CAM" }
   } catch(e:Exception) {
    ui.post { status.text="Descarga fallida: ${e.message}" }
   } finally {
    conn?.disconnect()
    if(!complete) {
     destination?.let { contentResolver.delete(it,null,null) }
     legacyFile?.delete()
    }
   }
  }
 }
 private fun viewCameraFile(name:String,raw:String) {
  val uri=cameraUri(raw)
  if(uri==null) { status.text="Ruta de archivo no admitida: $raw"; return }
  val address=uri.toString()
  if(name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".mov")) {
   status.text="Abriendo video desde la cámara..."
   try { startActivity(Intent(Intent.ACTION_VIEW,uri).setDataAndType(uri,"video/*")) }
   catch(e:Exception) { status.text="No se pudo abrir el video: ${e.message}" }
   return
  }
  status.text="Cargando fotografía..."
  worker.execute {
   var conn:HttpURLConnection?=null
   try {
    conn=(URL(address).openConnection() as HttpURLConnection).apply { connectTimeout=5000; readTimeout=12000 }
    val code=conn.responseCode
    if(code!=200) { ui.post { status.text="La cámara no permite abrir esta ruta · HTTP $code" }; return@execute }
    val bytes=conn.inputStream.use { input ->
     val out=ByteArrayOutputStream()
     val buffer=ByteArray(8192)
     while(true) {
      val n=input.read(buffer)
      if(n<0) break
      if(out.size()+n>15_000_000) throw IllegalStateException("Archivo demasiado grande para vista previa")
      out.write(buffer,0,n)
     }
     out.toByteArray()
    }
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
    ui.post {
     if(bitmap==null) status.text="Archivo recibido, pero no es una imagen compatible"
     else {
      val image=ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER }
      android.app.AlertDialog.Builder(this).setTitle(name).setView(image).setPositiveButton("Cerrar",null).show()
      status.text="Fotografía abierta · ${bytes.size} bytes"
     }
    }
   } catch(e:Exception) { ui.post { status.text="No se pudo descargar la foto: ${e.message}" } }
   finally { conn?.disconnect() }
  }
 }
 private fun recordVideo(start:Boolean) {
  if(start && recordingRequested) { status.text="Ya se solicitó una grabación"; return }
  if(!start && !recordingRequested) { status.text="No hay grabación confirmada para detener"; return }
  recordingInfo.text=if(start) "Comprobando tarjeta microSD..." else "Solicitando detener grabación..."
  worker.execute {
   try {
    if(start) {
     val state=postJson("/osc/state","{}")
     val details=JSONObject(state.second).optJSONObject("state")
     val sd=details?.optBoolean("_sdCardState",false) ?: false
     val batteryLevel=details?.optDouble("batteryLevel",-1.0) ?: -1.0
     if(state.first!=200 || !sd || (batteryLevel>=0 && batteryLevel<0.1)) {
      ui.post {
       recordingInfo.text=if(!sd) "No se detecta microSD. No se puede confirmar almacenamiento." else "Revisa batería o conexión"
       status.text="Estado: HTTP ${state.first} · SD: $sd · batería: $batteryLevel"
      }
      return@execute
     }
    }
    val name=if(start) "camera.startCapture" else "camera.stopCapture"
    val result=postJson("/osc/commands/execute",JSONObject().put("name",name).toString())
    val json=JSONObject(result.second)
    val accepted=result.first==200 && json.optString("state")!="error"
    recordingRequested=if(start) accepted else if(accepted) false else recordingRequested
    ui.post {
     recordingInfo.text=when {
      start && accepted -> "Grabación solicitada · Comprueba indicador de cámara"
      !start && accepted -> "Detención aceptada · Comprueba archivo en microSD"
      else -> "La cámara rechazó la orden de grabación"
     }
     status.text="HTTP ${result.first} · ${result.second.take(600)}"
    }
   } catch(e:Exception) {
    ui.post { recordingInfo.text="Error de grabación"; status.text=e.message ?: "Error de conexión" }
   }
  }
 }
 private fun postJson(path:String,body:String):Pair<Int,String> {
  val conn=(URL(base+path).openConnection() as HttpURLConnection).apply {
   requestMethod="POST"; doOutput=true; connectTimeout=5000; readTimeout=10000
   setRequestProperty("Content-Type","application/json")
  }
  return try {
   conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
   val code=conn.responseCode
   val response=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
   Pair(code,response)
  } finally { conn.disconnect() }
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
  status.text="Conectando vista previa..."
  previewWorker.execute {
   var frames=0
   var attempts=0
   var lastError="Sin datos de video"
   while(previewOn && attempts<120) {
    attempts++
    var conn: HttpURLConnection?=null
    try {
     conn=(URL("$base/osc/commands/execute").openConnection() as HttpURLConnection).apply {
      requestMethod="POST"
      doOutput=true
      connectTimeout=5000
      readTimeout=8000
      setRequestProperty("Content-Type","application/json")
      setRequestProperty("Connection","keep-alive")
     }
     previewConnection=conn
     conn.outputStream.use { it.write("""{"name":"camera.getLivePreview"}""".toByteArray(Charsets.UTF_8)) }
     val code=conn.responseCode
     val type=(conn.contentType ?: "").lowercase()
     if(code!=200 || (!type.contains("image") && !type.contains("multipart") && !type.contains("octet-stream"))) {
      val errorStream=if(code in 200..299) conn.inputStream else conn.errorStream
      val body=errorStream?.bufferedReader()?.use { it.readText().take(250) } ?: ""
      lastError="HTTP $code · $body"
      break
     }
     conn.inputStream.buffered(16384).use { input ->
      val frame=ByteArrayOutputStream(65536)
      val chunk=ByteArray(8192)
      var previous=-1
      var inJpeg=false
      while(previewOn) {
       val count=input.read(chunk)
       if(count<0) { lastError="La cámara cerró el flujo de video"; break }
       for(i in 0 until count) {
        val b=chunk[i].toInt() and 0xff
        if(!inJpeg) {
         if(previous==0xff && b==0xd8) {
          frame.reset(); frame.write(0xff); frame.write(0xd8); inJpeg=true
         }
        } else {
         frame.write(b)
         if(frame.size()>4_000_000) { frame.reset(); inJpeg=false }
         else if(previous==0xff && b==0xd9) {
          val bytes=frame.toByteArray()
          val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
          if(bitmap!=null) {
           frames++
           val countFrames=frames
           ui.post {
            if(previewOn) {
             preview.setImageBitmap(bitmap)
             status.text="Video recibido · $countFrames fotogramas"
            }
           }
          }
          frame.reset(); inJpeg=false
         }
        }
        previous=b
       }
      }
     }
    } catch(e:Exception) {
     lastError="${e.javaClass.simpleName}: ${e.message}"
    } finally {
     conn?.disconnect()
     previewConnection=null
    }
    if(previewOn && attempts<120) {
     val retry=attempts
     ui.post { if(previewOn) status.text="Continuando vista previa · solicitud $retry · $frames fotogramas" }
     try { Thread.sleep(250) } catch(_:InterruptedException) { break }
    }
   }
   val stillActive=previewOn
   previewOn=false
   ui.post {
    previewButton.text="INICIAR VISTA PREVIA"
    if(stillActive) {
     status.text="Vista previa finalizada · $frames fotogramas · $lastError"
     if(frames==0) onUnavailable()
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
