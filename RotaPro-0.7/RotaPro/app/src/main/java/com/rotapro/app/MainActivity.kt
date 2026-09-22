package com.rotapro.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private data class Stop(val name:String,val address:String,val phone:String="",val note:String="",val lat:Double?=null,val lon:Double?=null,val done:Boolean=false)
private data class RoadRoute(val ordered:List<Stop>,val km:Double,val minutes:Int,val geometry:List<Pair<Double,Double>>,val start:Pair<Double,Double>)

class MainActivity:ComponentActivity(){
    private var onImported:((List<Stop>)->Unit)?=null
    private val openCsv=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{importCsv(it)}}
    override fun onCreate(b:Bundle?){super.onCreate(b);AppCtx.ctx=applicationContext;setContent{RotaProApp{cb->onImported=cb;openCsv.launch(arrayOf("text/*","text/csv","application/csv"))}}}
    private fun importCsv(uri:Uri){val out= mutableListOf<Stop>();contentResolver.openInputStream(uri)?.use{input->BufferedReader(InputStreamReader(input,Charsets.UTF_8)).useLines{ls->ls.drop(1).forEach{line->val c=parseCsv(line);if(c.size>=2&&c[0].isNotBlank()&&c[1].isNotBlank())out+=Stop(c[0].trim(),c[1].trim(),c.getOrNull(2)?.trim().orEmpty(),c.getOrNull(3)?.trim().orEmpty())}}};onImported?.invoke(out)}
    private fun parseCsv(line:String):List<String>{val d=if(line.count{it==';'}>line.count{it==','})';' else ',';val o= mutableListOf<String>();val s=StringBuilder();var q=false;line.forEach{ch->when{ch=='"'->q=!q;ch==d&&!q->{o+=s.toString();s.clear()};else->s.append(ch)}};o+=s.toString();return o}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RotaProApp(onPickCsv:(((List<Stop>)->Unit)->Unit)){
    var screen by remember{ mutableStateOf("home")};var stops by remember{ mutableStateOf(listOf<Stop>())};var base by remember{ mutableStateOf("")};var name by remember{ mutableStateOf("")};var address by remember{ mutableStateOf("")};var phone by remember{ mutableStateOf("")};var note by remember{ mutableStateOf("")};var route by remember{ mutableStateOf<RoadRoute?>(null)};var busy by remember{ mutableStateOf(false)};var msg by remember{ mutableStateOf("")};val scope=rememberCoroutineScope();val ctx=LocalContext.current
    fun add(){if(name.isNotBlank()&&address.isNotBlank()){stops=stops+Stop(name.trim(),address.trim(),phone.trim(),note.trim());name="";address="";phone="";note="";route=null}}
    MaterialTheme{Scaffold(topBar={TopAppBar(title={Text("RotaPro 0.6")})}){pad->Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()){
        when(screen){
            "home"->{Text("Roteirizador de entregas",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(8.dp));Text("${stops.size} entregas cadastradas");Spacer(Modifier.height(18.dp));Button({screen="stops"},Modifier.fillMaxWidth()){Text("Entregas")};Spacer(Modifier.height(8.dp));OutlinedButton({onPickCsv{stops=it;route=null;msg="${it.size} entregas importadas."}},Modifier.fillMaxWidth()){Text("Importar CSV")};Spacer(Modifier.height(8.dp));Button({screen="route"},Modifier.fillMaxWidth(),enabled=stops.isNotEmpty()){Text("Planejar rota")};if(msg.isNotBlank()){Spacer(Modifier.height(12.dp));Text(msg)}}
            "stops"->{Text("Entregas",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(8.dp));OutlinedTextField(name,{name=it},label={Text("Cliente")},modifier=Modifier.fillMaxWidth());OutlinedTextField(address,{address=it},label={Text("Endereço completo")},modifier=Modifier.fillMaxWidth());OutlinedTextField(phone,{phone=it},label={Text("Telefone")},modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("Observação")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));Button(::add,Modifier.fillMaxWidth()){Text("Adicionar")};Spacer(Modifier.height(8.dp));LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){itemsIndexed(stops){i,s->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("${i+1}. ${s.name}");Text(s.address)}}}};Button({screen="home"},Modifier.fillMaxWidth()){Text("Voltar")}}
            "route"->{Text("Rota pelas ruas",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(8.dp));OutlinedTextField(base,{base=it},label={Text("Base / ponto de saída")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({scope.launch{val p=currentLocation(ctx);if(p!=null){base="Minha localização atual";msg="Posição atual definida como saída.";route=calculateRoadRoute(null,stops,p)}else msg="Ative o GPS e permita localização."}},Modifier.weight(1f)){Text("Usar meu GPS")};Button({busy=true;msg="Calculando rota pelas ruas...";scope.launch{route=calculateRoadRoute(base.takeIf{it!="Minha localização atual"},stops,null);busy=false;msg=route?.let{"Rota: %.1f km • ~%d min".format(it.km,it.minutes)}?:"Não foi possível calcular. Verifique internet e endereços."}},Modifier.weight(1f),enabled=!busy&&base.isNotBlank()){Text("OTIMIZAR")}};if(busy){Spacer(Modifier.height(8.dp));LinearProgressIndicator(Modifier.fillMaxWidth())};if(msg.isNotBlank()){Spacer(Modifier.height(8.dp));Text(msg)};route?.let{r->Spacer(Modifier.height(8.dp));Button({screen="map"},Modifier.fillMaxWidth()){Text("VER MAPA DA ROTA")};Spacer(Modifier.height(8.dp));LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){itemsIndexed(r.ordered){i,s->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("${i+1}. ${s.name}");Text(s.address);TextButton({openNavigation(ctx,s)}){Text("Navegar")}}}}}}?:Spacer(Modifier.weight(1f));Button({screen="home"},Modifier.fillMaxWidth()){Text("Voltar")}}
            "map"->{route?.let{r->Text("Mapa • %.1f km • ~%d min".format(r.km,r.minutes),style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(8.dp));RouteMap(r,Modifier.weight(1f).fillMaxWidth());Spacer(Modifier.height(8.dp));Button({screen="route"},Modifier.fillMaxWidth()){Text("Voltar à rota")}}}
        }
    }}}
}

@Composable private fun RouteMap(r:RoadRoute,modifier:Modifier){val html=remember(r){mapHtml(r)};AndroidView(factory={c->WebView(c).apply{settings.javaScriptEnabled=true;webViewClient=WebViewClient();loadDataWithBaseURL("https://localhost/",html,"text/html","UTF-8",null)}},modifier=modifier)}
private fun mapHtml(r:RoadRoute):String{val pts=r.geometry.joinToString(","){"[${it.first},${it.second}]"};val marks=r.ordered.mapIndexed{i,s->"L.marker([${s.lat},${s.lon}]).addTo(map).bindPopup('${i+1}. ${js(s.name)}');"}.joinToString("\n");return """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/><style>html,body,#m{height:100%;margin:0}</style></head><body><div id='m'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>var map=L.map('m');L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap'}).addTo(map);var line=L.polyline([$pts]).addTo(map);L.marker([${r.start.first},${r.start.second}]).addTo(map).bindPopup('Saída');$marks map.fitBounds(line.getBounds(),{padding:[20,20]});</script></body></html>"""}
private fun js(s:String)=s.replace("\\","\\\\").replace("'","\\'")

private suspend fun calculateRoadRoute(baseAddress:String?,stops:List<Stop>,startGps:Pair<Double,Double>?):RoadRoute?=withContext(Dispatchers.IO){
    val start=startGps?:geocode(baseAddress?:return@withContext null)?:return@withContext null
    val located=stops.mapNotNull{s->geocode(s.address)?.let{s.copy(lat=it.first,lon=it.second)}};if(located.isEmpty())return@withContext null
    try{val coords=(listOf(start)+located.map{it.lat!! to it.lon!!}).joinToString(";"){"${it.second},${it.first}"};val url=URL("https://router.project-osrm.org/trip/v1/driving/$coords?source=first&roundtrip=false&overview=full&geometries=geojson&steps=false");val con=(url.openConnection() as HttpURLConnection).apply{connectTimeout=15000;readTimeout=20000;setRequestProperty("User-Agent","RotaPro-MVP/0.6")};val text=con.inputStream.bufferedReader().readText();val j=JSONObject(text);if(j.optString("code")!="Ok")return@withContext null;val trip=j.getJSONArray("trips").getJSONObject(0);val way=j.getJSONArray("waypoints");val ordered=located.indices.sortedBy{i->way.getJSONObject(i+1).getInt("waypoint_index")}.map{located[it]};val c=trip.getJSONObject("geometry").getJSONArray("coordinates");val geom=(0 until c.length()).map{i->val a=c.getJSONArray(i);a.getDouble(1) to a.getDouble(0)};RoadRoute(ordered,trip.getDouble("distance")/1000.0,(trip.getDouble("duration")/60.0).toInt(),geom,start)}catch(_:Exception){null}
}
private fun geocode(address:String):Pair<Double,Double>?{return try{val c=AppCtx.ctx?:return null;val g=Geocoder(c,Locale("pt","BR"));g.getFromLocationName(address,1)?.firstOrNull()?.let{it.latitude to it.longitude}}catch(_:Exception){null}}
private suspend fun currentLocation(ctx:Context):Pair<Double,Double>?=withContext(Dispatchers.IO){if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return@withContext null;try{val lm=ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager;val p=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).mapNotNull{runCatching{lm.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull{it.time};p?.let{it.latitude to it.longitude}}catch(_:Exception){null}}
private fun openNavigation(ctx:Context,s:Stop){val q=URLEncoder.encode(s.address,"UTF-8");val i=Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q=$q"));if(i.resolveActivity(ctx.packageManager)!=null)ctx.startActivity(i)else ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$q")))}
private object AppCtx{var ctx:Context?=null}
