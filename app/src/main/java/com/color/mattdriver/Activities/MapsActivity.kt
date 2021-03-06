package com.color.mattdriver.Activities

import android.Manifest
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.color.mattdriver.Constants
import com.color.mattdriver.Fragments.*
import com.color.mattdriver.Models.*
import com.color.mattdriver.R
import com.color.mattdriver.Utilities.Apis
import com.color.mattdriver.Utilities.LocationUpdatesBroadcastReceiver
import com.color.mattdriver.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule


class MapsActivity : AppCompatActivity(),
    OnMapReadyCallback,
    Welcome.WelcomeInterface,
    JoinOrganisation.JoinOrganisationInterface,
    CreateOrganisation.CreateOrganisationInterface,
    OrganisationPasscode.OrganisationPasscodeInterface,
    ViewOrganisation.viewOrganisationInterface,
    GoogleMap.OnMyLocationClickListener,
    GoogleMap.OnMyLocationButtonClickListener,
    GoogleMap.OnMarkerClickListener,
    ViewRoute.ViewRouteInterface,
    MainSettings.MainSettingsInterface,
    SignUp.SignUpInterface,
    SignIn.SignInInterface,
    Drivers.DriversInterface,
    ViewAllRoutes.ViewAllRoutesInterface
{
    val TAG = "MapsActivity"
    val _welcome = "_welcome"
    val _join_organisation = "_join_organisation"
    val _create_organisation = "_create_organisation"
    val _organisation_passcode =  "_organisation_passcode"
    val _view_organisation = "_view_organisation"
    val _settings = "_settings"
    val _view_route = "_view_route"
    val _sign_up = "sign_up"
    val _sign_in = "_sign_in"
    val _view_drivers = "_view_drivers"
    val _view_all_routes = "_view_all_routes"

    private lateinit var binding: ActivityMapsBinding
    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private val locationRequestCode = 1000
    private var wayLatitude = 0.0
    private var wayLongitude = 0.0
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    val constants = Constants()
    var is_loading = false

    val db = Firebase.firestore
    var organisations: ArrayList<organisation> = ArrayList()
    var my_organisations: ArrayList<String> = ArrayList()
    var active_organisation = ""
    var routes: ArrayList<route> = ArrayList()
    var active_route = ""

    var mapView: View? = null
    val ZOOM = 15f
    val ZOOM_FOCUSED = 16f
    var has_set_my_location = false

    lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    var mLastKnownLocations: ArrayList<LatLng> = ArrayList()
    var is_creating_routes = false

    var set_start_pos: LatLng? = null
    var set_start_pos_desc = ""
    var set_start_pos_id = ""
    var start_pos_geo_data: geo_data.reverseGeoData? = null

    var set_end_pos: LatLng? = null
    var set_end_pos_desc = ""
    var set_end_pos_id = ""
    var end_pos_geo_data: geo_data.reverseGeoData? = null

    var added_bus_stops: ArrayList<bus_stop> = ArrayList()
    var drawn_polyline: ArrayList<Polyline> = ArrayList()
    var route_directions_data: directions_data? = null

    var AUTOCOMPLETE_REQUEST_CODE = 1
    var viewing_route: route? = null
    var my_marker: Marker? = null
    var my_marker_trailing_markers: ArrayList<Circle> = ArrayList()

    var can_share_location = false
    var sharedPreferences: SharedPreferences? = null





    private val pendingIntent: PendingIntent get() {
            val intent = Intent(this, LocationUpdatesBroadcastReceiver::class.java)
            intent.action = LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES
            return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

    class LocationUpdatesBroadcastReceive : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {

        }

        var when_location_received: (loc: Location) -> Unit = {}

        companion object {
            private val TAG = "LUBroadcastReceiver"
            internal val ACTION_PROCESS_UPDATES = "com.google.android.gms.location.sample.locationupdatespendingintent.action" + ".PROCESS_UPDATES"
        }
    }

    var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if(intent.hasExtra("loc")) {
                val it = Gson().fromJson(intent.extras!!.get("loc") as String, Location::class.java)

            }
        }
    }

    var sharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener = object : SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
                if (key == constants.loc_data) {
                    // Write your code here
                    val loc_stirng = sharedPreferences!!.getString(constants.loc_data,"")
                    if(!loc_stirng.equals("")) {
                        val it = Gson().fromJson(loc_stirng, LatLng::class.java)

                        wayLatitude = it.latitude
                        wayLongitude = it.longitude
                        Log.e(TAG, "received lat: ${wayLatitude} received long: ${wayLongitude}")
                        if (!has_set_my_location) {
                            has_set_my_location = true
                            mMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(
                                        it.latitude,
                                        it.longitude), ZOOM
                                )
                            )
                        }
                        mLastKnownLocations.add(it)
                        when_location_gotten()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG,"onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val actionBar: ActionBar = supportActionBar!!
        actionBar.hide()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapView = mapFragment.view
        mapFragment.getMapAsync(this)

        set_up_getting_my_location()
        set_network_change_receiver()

//        if(constants.SharedPreferenceManager(applicationContext).isFirstTimeLaunch()){
//            open_welcome_fragment()
//        }
//
        binding.continueLayout.setOnClickListener {
            constants.touch_vibrate(applicationContext)
            if(active_organisation.equals("")){
                val orgs = Gson().toJson(organisation.organisation_list(organisations))
                supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                    .replace(binding.money.id,JoinOrganisation.newInstance("","", orgs),_join_organisation).commit()
            }else{
                if(get_active_org()!=null){
                    var org_string = Gson().toJson(get_active_org())
                    var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(get_active_org()!!)))
                    supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                        .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string,active_route),_view_organisation).commit()

                }
            }
        }

        binding.viewLayout.setOnClickListener {
            var org_string = Gson().toJson(get_active_org())
            var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(get_active_org()!!)))
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string,active_route),_view_organisation).commit()

        }

        binding.settings.setOnClickListener {
            constants.touch_vibrate(applicationContext)
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(binding.money.id,MainSettings.newInstance("",""),_settings).commit()
//            binding.settings.visibility = View.GONE
        }

        if(constants.SharedPreferenceManager(applicationContext).get_current_data().equals("")){
            //first time, data has to be loaded
            Log.e(TAG,"loading data from firestore")
            load_organisations()
            load_my_organisations()
            load_routes()
        }else{
            Log.e(TAG,"setting session data")
            set_session_data()
            Log.e("MapsAct","organisations are : ${organisations.size}")
        }

        if(supportFragmentManager.findFragmentByTag(_settings)!=null){
            Log.e(TAG,"Were in settings, hiding settings btn")
//            binding.settings.visibility = View.GONE
        }

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        Places.initialize(applicationContext, Apis().places_api_key)
        val placesClient: PlacesClient = Places.createClient(this)

        binding.shareLocationSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            constants.touch_vibrate(applicationContext)
            can_share_location = isChecked
            if(isChecked){
                binding.shareLocationText.text = "Stop sharing location"
                load_notification(true)
                binding.busIcon.setImageResource(R.drawable.bus_loc_shared)
            }else{
                binding.shareLocationText.text = "Start sharing location"
                remove_notification()
                binding.busIcon.setImageResource(R.drawable.bus_loc)
            }
        }

        when_active_org_set()
        when_active_route_set()

        createNotificationChannel()
        binding.busIcon.setOnClickListener {
            move_camera_to_my_location()
        }
        binding.title.setOnClickListener {
            if(get_active_route()!=null){
                show_all_markers()
            }
        }

        set_up_driver_listeners()

//        val filter = IntentFilter()
//        filter.addAction("com.example.Broadcast")
//        val receiver = LocationUpdatesBroadcastReceive()
//        receiver.when_location_received = {
//
//        }
//        registerReceiver(receiver, filter)

//        registerReceiver(broadcastReceiver, IntentFilter("INTERNET_LOST"))

        sharedPreferences = getSharedPreferences(constants.loc_data, Context.MODE_PRIVATE)
        sharedPreferences?.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

    }





    var route_views_listener: ListenerRegistration? = null
    var route_views: ArrayList<route_view> = ArrayList()
    var route_views_items: ArrayList<String> = ArrayList()
    fun set_route_views_listener(route: route){
//        route_views.clear()
//        route_views_items.clear()
        route_views_listener?.remove()

        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
        route_views_listener = db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_routes)
            .document(route.route_id)
            .collection(constants.views)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "listen:error", e)
                    return@addSnapshotListener
                }

                for (dc in snapshots!!.documentChanges) {
                    if(dc.type.equals(DocumentChange.Type.ADDED)){
//                            Log.d(TAG, "New location: ${dc.document.data}")
                    }else if(dc.type.equals(DocumentChange.Type.MODIFIED)){
//                            Log.d(TAG, "Modified location: ${dc.document.data}")
                    }else if(dc.type.equals(DocumentChange.Type.REMOVED)){
//                            Log.d(TAG, "Removed location: ${dc.document.data}")
                    }

                    val view_id = dc.document["view_id"] as String
                    val creation_time = dc.document["creation_time"] as Long
                    val route_id = dc.document["route"] as String
                    val driver_id = dc.document["driver_id"] as String
                    val viewer_id = dc.document["viewer_id"] as String
                    val viewer_lat_lng = Gson().fromJson(dc.document["viewer_lat_lng"] as String, LatLng::class.java)

                    val route_viewing = route_view(view_id,creation_time,route_id,driver_id,viewer_id,viewer_lat_lng)
                    if(!route_views_items.contains(view_id)){
                        route_views.add(route_viewing)
                        route_views_items.add(view_id)
                        when_route_viewed(route_viewing)
                    }
                }
            }
    }

    fun get_route_views_first(route: route){
        route_views.clear()
        route_views_items.clear()
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_routes)
            .document(route.route_id)
            .collection(constants.views)
            .get().addOnSuccessListener {
                whenNetworkAvailable()
                if(!it.isEmpty){
                    for(dc in it){
                        val view_id = dc["view_id"] as String
                        val creation_time = dc["creation_time"] as Long
                        val route_id = dc["route"] as String
                        val driver_id = dc["driver_id"] as String
                        val viewer_id = dc["viewer_id"] as String
                        val viewer_lat_lng = Gson().fromJson(dc["viewer_lat_lng"] as String, LatLng::class.java)

                        val route_viewing = route_view(view_id,creation_time,route_id,driver_id,viewer_id,viewer_lat_lng)
                        if(!route_views_items.contains(view_id)){
                            route_views.add(route_viewing)
                            route_views_items.add(view_id)
                        }
                    }

                }
                set_route_views_listener(route)
            }.addOnFailureListener {
                whenNetworkLost()
            }
    }

    class route_view(var view_id: String, var creation_time: Long, var route: String, var driver_id: String, var viewer_id: String, var viewer_lat_lng: LatLng)

    class route_view_list(var route_views: ArrayList<route_view>)

    fun open_welcome_fragment(){
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(binding.money.id,Welcome.newInstance("",""),_welcome).commit()
    }

    override fun OnContinueSelected() {
        constants.SharedPreferenceManager(applicationContext).setFirstTimeLaunch(false)
        onBackPressed()

    }

    private var doubleBackToExitPressedOnce = false
    override fun onBackPressed() {
        if(is_location_picker_open){
            close_location_picker()
        }else {
            if (supportFragmentManager.fragments.size > 1) {
                val trans = supportFragmentManager.beginTransaction()
                trans.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
                val currentFragPos = supportFragmentManager.fragments.size - 1

                removing_fragment_notifier(supportFragmentManager.fragments.get(currentFragPos).tag!!)

                if(!is_creating_routes){
                    trans.remove(supportFragmentManager.fragments.get(currentFragPos))
                    trans.commit()
                    supportFragmentManager.popBackStack()
                }else{
                    is_creating_routes = false
                    show_normal_home_items()
                }

            } else {
                if (doubleBackToExitPressedOnce) {
                    super.onBackPressed()
                    return
                }

                this.doubleBackToExitPressedOnce = true
                Toast.makeText(this, "Press BACK again to exit", Toast.LENGTH_SHORT).show()

                Handler().postDelayed(Runnable { doubleBackToExitPressedOnce = false }, 2000)
            }
        }
    }

    fun removing_fragment_notifier(tag: String){
        if(tag.equals(_view_organisation) || tag.equals(_view_all_routes)){
            if(viewing_route!=null){
                open_route_info_with_anim = false
                viewRoute(viewing_route!!, get_active_org()!!)
            }
            viewing_route = null
        }
    }



    fun set_network_change_receiver(){
        val networkCallback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network?) {
                // network available
                whenNetworkAvailable()
            }

            override fun onLost(network: Network?) {
                // network unavailable
                whenNetworkLost()
            }
        }

        val connectivityManager: ConnectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val request: NetworkRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        if(!constants.isOnline(applicationContext)){
            whenNetworkLost()
        }
    }

    fun whenNetworkLost(){
        val alpha_hidden = constants.dp_to_px(-20f, applicationContext)
        val alpha_shown = 0f
        val duration = 200L
        val delay = 1000L
        val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message?) {
                binding.networkRelative.visibility = View.VISIBLE
                binding.noInternetText.text = getString(R.string.no_internet_connection)
                binding.noInternetText.setBackgroundColor(resources.getColor(R.color.red))
                val valueAnimator = ValueAnimator.ofFloat(alpha_hidden, alpha_shown)
                val listener = ValueAnimator.AnimatorUpdateListener{
                    val value = it.animatedValue as Float
                    binding.networkRelative.translationY = value
                }
                valueAnimator.addUpdateListener(listener)
                valueAnimator.interpolator = LinearOutSlowInInterpolator()
                valueAnimator.duration = duration
                valueAnimator.start()
            }
        }

        Timer().schedule(100){
            val message = mHandler.obtainMessage()
            message.sendToTarget()
        }
    }

    fun whenNetworkAvailable(){
        val alpha_hidden = constants.dp_to_px(-20f, applicationContext)
        val alpha_shown = 0f
        val duration = 200L
        val delay = 1000L

        val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message?) {
                binding.noInternetText.text = getString(R.string.back_online)
                binding.noInternetText.setBackgroundColor(resources.getColor(R.color.green))
                val valueAnimator = ValueAnimator.ofFloat(alpha_shown, alpha_hidden)
                val listener = ValueAnimator.AnimatorUpdateListener{
                    val value = it.animatedValue as Float
                    binding.networkRelative.translationY = value
                    if(value==alpha_hidden){
                        binding.networkRelative.visibility = View.GONE
                    }
                }
                valueAnimator.addUpdateListener(listener)
                valueAnimator.interpolator = LinearOutSlowInInterpolator()
                valueAnimator.duration = duration
                valueAnimator.startDelay = delay
                valueAnimator.start()
            }
        }

        Timer().schedule(100){
            val message = mHandler.obtainMessage()
            message.sendToTarget()
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            locationRequestCode -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    set_up_getting_my_location()
                } else {
//                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var loc_acc: Float = 1f
    fun set_up_getting_my_location(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), locationRequestCode)
        } else{
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            locationRequest = LocationRequest.create()
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest.setInterval(constants.update_interval)
            locationRequest.setFastestInterval(constants.fast_update_interval)


            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (locationResult == null) {
                        return
                    }
                    for (location in locationResult.locations) {
                        if (location != null) {
                            loc_acc = location.accuracy
                            wayLatitude = location.latitude
                            wayLongitude = location.longitude
                            Log.e(TAG,"wayLatitude: ${wayLatitude} longitude: ${wayLongitude}")
                            if(!has_set_my_location){
                                has_set_my_location = true
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude,
                                    location.longitude), ZOOM))
                            }
                            mLastKnownLocations.add(LatLng(location.latitude,location.longitude))
                            when_location_gotten()
                        }
                    }
                }
            }
//            mFusedLocationClient.requestLocationUpdates(locationRequest,locationCallback,null)
            mFusedLocationClient.requestLocationUpdates(locationRequest,pendingIntent)
        }
    }

    override fun onDestroy() {
        Log.e(TAG,"onDestroy")
        super.onDestroy()
        if (this.mFusedLocationClient != null) {
//            mFusedLocationClient.removeLocationUpdates(locationCallback)
            mFusedLocationClient.removeLocationUpdates(pendingIntent)
        }
//        unregisterReceiver(broadcastReceiver)
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        route_views_listener?.remove()
        store_session_data()
        remove_notification()
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG,"onStop")

    }

    override fun onStart() {
        Log.e(TAG,"onStart")
        super.onStart()
        set_session_data()

        Constants().maintain_theme(applicationContext)
        if(!intent.hasExtra(constants.intent_source)){
        }
    }

    override fun onPause() {
        Log.e(TAG,"onPause")
        super.onPause()
    }

    override fun onResume() {
        Log.e(TAG,"onResume")
        super.onResume()
    }




    fun hideLoadingScreen(){
        is_loading = false
        binding.mapsLoadingScreen.visibility = View.GONE
    }

    fun showLoadingScreen(){
        is_loading = true
        binding.mapsLoadingScreen.visibility = View.VISIBLE
        binding.mapsLoadingScreen.setOnTouchListener { v, _ -> true }

    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().getDisplayMetrics().density).toInt()
    }

    fun load_organisations(){
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
        organisations.clear()
        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_organisations)
            .get().addOnSuccessListener {
                if(it.documents.isNotEmpty()){
                    for(item in it.documents){
                        val org_id = item["org_id"] as String
                        val org_name = item["name"] as String
                        val country = item["country"] as String
                        val creation_time = item["creation_time"] as Long

                        val org = organisation(org_name,creation_time)
                        org.org_id = org_id
                        org.country = country

                        if(item.contains("admins")){
                            org.admins  = Gson().fromJson(item["admins"] as String, organisation.admin::class.java)
                        }
                        if(item.contains("deactives")){
                            org.deactivated_drivers = Gson().fromJson(item["deactives"] as String, organisation.deactive_drivers::class.java)
                        }

                        organisations.add(org)
                    }
                }
                if(organisations.isNotEmpty()){
                    load_organisation_drivers()
                    set_up_driver_listeners()
                }else{
                    store_session_data()
                    if(supportFragmentManager.findFragmentByTag(_join_organisation)!=null){
                        (supportFragmentManager.findFragmentByTag(_join_organisation) as JoinOrganisation)
                            .onOrganisationListReloaded(organisations)
                    }
                    if(get_active_org()!=null && supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                        (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                            .onOrganisationReloaded(get_active_org()!!)
                    }
                    if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
                        (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes)
                            .onOrganisationReloaded(get_active_org()!!)
                    }
                }
            }
    }

    var driver_iter = 0
    fun load_organisation_drivers(){
        driver_iter = 0
        for(org in organisations){
            val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
            val time = Calendar.getInstance().timeInMillis
            db.collection(constants.organisations)
                .document(user.phone.country_name)
                .collection(constants.country_organisations)
                .document(org.org_id!!)
                .collection(constants.drivers)
                .get().addOnSuccessListener {
                    if(!it.isEmpty){
                        for(doc in it.documents) {
                            val driver_id = doc["driver_id"] as String
                            val org_id = doc["org_id"] as String
                            val join_time = doc["join_time"] as Long

                            val driver = driver(driver_id,org_id, join_time)
                            for(item in organisations){
                                if(item.org_id.equals(org_id)){
                                    item.drivers.add(driver)
                                }
                            }
                        }
                    }
                    driver_iter+=1
                    if(driver_iter >= organisations.size){
                        //were done
                        store_session_data()
                        if(supportFragmentManager.findFragmentByTag(_join_organisation)!=null){
                            (supportFragmentManager.findFragmentByTag(_join_organisation) as JoinOrganisation)
                                .onOrganisationListReloaded(organisations)
                        }
                        if(get_active_org()!=null && supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                            (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                                .onOrganisationReloaded(get_active_org()!!)
                        }
                        if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
                            (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes)
                                .onOrganisationReloaded(get_active_org()!!)
                        }
                    }
                    whenNetworkAvailable()
                }.addOnFailureListener{
                    Toast.makeText(applicationContext, "Something went wrong",Toast.LENGTH_SHORT).show()
                    whenNetworkLost()
                }
        }
    }

    fun load_routes(){
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
        routes.clear()
        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_routes)
            .get().addOnSuccessListener {
                if(it.documents.isNotEmpty()){
                    for(item in it.documents) {
                        val organisation_id = item["organisation_id"] as String
                        val creation_time = item["creation_time"] as Long
                        val route_id = item["route_id"] as String
                        val country = item["country"] as String
                        val creater = item["creater"] as String

                        val route = Gson().fromJson(item["route"].toString(), route::class.java)
                        var disabled = false
                        if(item.contains("disabled")){
                            disabled = item["disabled"] as Boolean
                        }
                        route.disabled = disabled

                        routes.add(route)
                    }
                }
                store_session_data()
                if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null && get_active_org()!=null){
                    (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                        .when_route_data_updated(load_my_organisations_routes(get_active_org()!!))
                }
                if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null && get_active_org()!=null){
                    (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes)
                        .when_route_data_updated(load_my_organisations_routes(get_active_org()!!))
                }
            }
    }

    fun load_my_organisations(){
        my_organisations.clear()
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        db.collection(constants.coll_users).document(uid)
            .collection(constants.my_organisations).get()
            .addOnSuccessListener {
                if(it.documents.isNotEmpty()){
                    for(item in it.documents){
                        val org_id = item["org_id"] as String
                        my_organisations.add(org_id)
                    }
                }
                store_session_data()
            }
    }




    override fun whenReloadOrganisations() {
        load_organisations()
        load_routes()
    }

    override fun whenCreateOrganisation() {
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id,CreateOrganisation.newInstance("",""),_create_organisation).commit()
    }

    override fun joinOrganisation(organisation: organisation) {
        if(my_organisations.contains(organisation.org_id)){
            //im a part of this
            remove_active_route_on_map()
            active_route = ""
            when_active_org_set()
            var org_string = Gson().toJson(organisation)
            var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(organisation)))
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string,active_route),_view_organisation).commit()
        }else{
            val org = Gson().toJson(organisation)
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .add(binding.money.id,OrganisationPasscode.newInstance("","", org),_organisation_passcode).commit()
        }
    }

    override fun whenCreateOrganisationContinue(name: String, country_name: String) {
        showLoadingScreen()

        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val org_ref = db.collection(constants.organisations)
            .document(country_name)
            .collection(constants.country_organisations)
            .document()

        val time = Calendar.getInstance().timeInMillis
        val admins: ArrayList<String> = ArrayList()
        val deactives: ArrayList<String> = ArrayList()
        admins.add(constants.pass)//mod

        val data = hashMapOf(
            "name" to name,
            "org_id" to org_ref.id,
            "country" to country_name,
            "creater" to uid,
            "creation_time" to time,
            "admins"  to Gson().toJson(organisation.admin(admins)),
            "deactives" to Gson().toJson(organisation.deactive_drivers(deactives))
        )

        val new_org = organisation(name,time)
        new_org.org_id = org_ref.id
        new_org.country = country_name
        new_org.admins = organisation.admin(admins)

        organisations.add(new_org)

        org_ref.set(data).addOnSuccessListener {
            Toast.makeText(applicationContext,"Done", Toast.LENGTH_SHORT).show()
            hideLoadingScreen()
            onBackPressed()

            my_organisations.add(new_org.org_id!!)
            var org_string = Gson().toJson(new_org)
            var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(new_org)))
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,
                    route_string,active_route),_view_organisation).commit()
            active_organisation = new_org.org_id!!
            when_active_org_set()
        }


        if(supportFragmentManager.findFragmentByTag(_join_organisation)!=null){
            (supportFragmentManager.findFragmentByTag(_join_organisation) as JoinOrganisation)
                .onOrganisationListUpdated(organisations)
        }

        db.collection(constants.coll_users).document(uid)
            .collection(constants.my_organisations).document(org_ref.id)
            .set(hashMapOf(
                "name" to name,
                "org_id" to org_ref.id,
                "creation_time" to time
            ))
    }




    override fun submitOrganisationPasscode(code: Long, organisation: organisation) {
        showLoadingScreen()
        db.collection(constants.otp_codes)
            .document(organisation.org_id!!)
            .collection(constants.code_instances)
            .get().addOnSuccessListener {
                hideLoadingScreen()
                if(!it.documents.isEmpty()){
                    var does_code_work = false
                    for(item in it.documents){
                        val item_code = item["code"] as Long
                        val item_creation_time = item["creation_time"] as Long
                        val item_organisation = item["organisation"] as String
                        val time_difference = Calendar.getInstance().timeInMillis - item_creation_time

                        if(item_code==code && time_difference < constants.otp_expiration_time
                            && item_organisation.equals(organisation.org_id)){
                            //code works
                            does_code_work = true
                        }
                    }
                    if(does_code_work){
                        //if password is right
                        remove_active_route_on_map()
                        active_route = ""

                        my_organisations.add(organisation.org_id!!)
                        var org_string = Gson().toJson(organisation)
                        var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(organisation)))
                        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                            .replace(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string,active_route),_view_organisation).commit()

                        val uid = FirebaseAuth.getInstance().currentUser!!.uid
                        if(!constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!.email.equals(constants.unknown_email)) {
                            //the user is a registered user.
                            db.collection(constants.coll_users).document(uid)
                                .collection(constants.my_organisations)
                                .document(organisation.org_id!!)
                                .set(hashMapOf(
                                        "name" to organisation.name,
                                        "org_id" to organisation.org_id,
                                        "creation_time" to organisation.creation_time
                                    ))
                        }
                        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
                        val time = Calendar.getInstance().timeInMillis
                        db.collection(constants.organisations)
                            .document(user.phone.country_name)
                            .collection(constants.country_organisations)
                            .document(organisation.org_id!!)
                            .collection(constants.drivers)
                            .document(uid)
                            .set(hashMapOf(
                                "driver_id" to uid,
                                "org_id" to organisation.org_id,
                                "join_time" to time
                            ))
                        val driver = driver(uid,organisation.org_id!!, time)
                        for(item in organisations){
                            if(item.org_id.equals(organisation.org_id)){
                                item.drivers.add(driver)
                            }
                        }
                        active_organisation = organisation.org_id!!
                        when_active_org_set()
                        store_session_data()
                    }else{
                        //if password is wrong
                        if(supportFragmentManager.findFragmentByTag(_organisation_passcode)!=null){
                            (supportFragmentManager.findFragmentByTag(_organisation_passcode) as OrganisationPasscode).didPasscodeFail()
                        }
                    }
                }else{
                    //if password is wrong
                    if(supportFragmentManager.findFragmentByTag(_organisation_passcode)!=null){
                        (supportFragmentManager.findFragmentByTag(_organisation_passcode) as OrganisationPasscode).didPasscodeFail()
                    }
                }

            }
    }

    override fun createNewRouteClicked(organisation: organisation) {
        remove_all_added_pins()
        remove_drawn_route()

        added_bus_stops.clear()
        set_start_pos = null
        set_start_pos_desc = ""
        binding.startLocationDescription.text = ""
        set_start_pos_id = ""
        start_pos_geo_data= null

        set_end_pos= null
        set_end_pos_desc = ""
        binding.endLocationDescription.text = ""
        set_end_pos_id = ""
        end_pos_geo_data= null
        drawn_polyline.clear()
        route_directions_data = null

        binding.setBusStopTextview.text = getString(R.string.add_a_stop)
        binding.addRouteIcon.setImageResource(R.drawable.add_icon)
        is_picking_stop_location = false
        is_picking_source_location = false
        is_picking_destination_location = false

        openRouteCreater(organisation)
    }

    override fun generatePasscodeClicked(organisation: organisation, code: Long) {
        showLoadingScreen()
        db.collection(constants.otp_codes)
            .document(organisation.org_id!!)
            .collection(constants.code_instances)
            .document().set(hashMapOf(
                "code" to code,
                "organisation" to organisation.org_id,
                "creation_time" to Calendar.getInstance().timeInMillis
            )).addOnSuccessListener {
                hideLoadingScreen()
                Toast.makeText(applicationContext,"The password will only work for 1 min",Toast.LENGTH_SHORT).show()
                if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation).isPasscodeSet()
                }
            }

    }

    var open_route_info_with_anim = true
    override fun viewRoute(route: route, organisation: organisation) {
        var org_string = Gson().toJson(organisation)
        var route_string = Gson().toJson(route)
        var route_viewz = Gson().toJson(route_view_list(filter_views(route_views)))

        if(open_route_info_with_anim) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .add(binding.money.id, ViewRoute.newInstance("", "", org_string, route_string, active_route,route_viewz), _view_route).commit()
        }else{
            open_route_info_with_anim = true
            supportFragmentManager.beginTransaction()
                .add(binding.money.id, ViewRoute.newInstance("", "", org_string, route_string, active_route,route_viewz), _view_route).commit()
        }

    }





    override fun whenReloadRoutes() {
        load_routes()
        load_organisations()
    }

    override fun onChangeOrganisation() {
        val orgs = Gson().toJson(organisation.organisation_list(organisations))
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(binding.money.id,JoinOrganisation.newInstance("","", orgs),_join_organisation).commit()
    }

    override fun viewDrivers(organisation: organisation) {
        val org = Gson().toJson(organisation)
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id, Drivers.newInstance("","", org),_view_drivers).commit()
    }

    override fun viewAllRoutes() {
        var org_string = Gson().toJson(get_active_org())
        var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(get_active_org()!!)))

        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id,ViewAllRoutes.newInstance("","",org_string,route_string,active_route),_view_all_routes).commit()
    }

    fun store_session_data(){
        val session = Gson().toJson(session_data(organisations,active_organisation,active_route, my_organisations, routes))
        constants.SharedPreferenceManager(applicationContext).store_current_data(session)
    }

    fun set_session_data(){
        val session = constants.SharedPreferenceManager(applicationContext).get_current_data()
        if(!session.equals("")){
            //its not empty
            var session_obj = Gson().fromJson(session,session_data::class.java)
            organisations = session_obj.organisations
            my_organisations = session_obj.my_organisations
            routes = session_obj.routes
            active_organisation = session_obj.active_organisation
            active_route = session_obj.active_route
        }
    }

    class session_data(var organisations: ArrayList<organisation>,var active_organisation: String, var active_route: String,
                       var my_organisations: ArrayList<String>,var routes: ArrayList<route>): Serializable




    var auto_adjust_to_my_loc = false
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        if(Constants().SharedPreferenceManager(applicationContext).isDarkModeOn()) {
            val success = googleMap.setMapStyle(MapStyleOptions(resources.getString(R.string.style_json)))
            if (!success) {
                Log.e("mapp", "Style parsing failed.")
            }
        }

        mMap.setOnMyLocationClickListener(this)

        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
        mMap.setIndoorEnabled(false)
        mMap.setBuildingsEnabled(false)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true

        if (mapView!!.findViewById<View>("1".toInt()) != null) {
            // Get the button view
            val locationButton = (mapView!!.findViewById<View>("1".toInt())
                .getParent() as View).findViewById<View>("2".toInt())
            // and next place it, on bottom right (as Google Maps app)
            val layoutParams: RelativeLayout.LayoutParams = locationButton.layoutParams as RelativeLayout.LayoutParams
            // position on right bottom
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            layoutParams.setMargins(0, 0, dpToPx(10), dpToPx(100))
        }

        mMap.setOnMarkerClickListener(this)

        mMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(arg0: Marker) {

            }

            override fun onMarkerDragEnd(arg0: Marker) {
                update_marker(arg0.position)
            }

            override fun onMarkerDrag(arg0: Marker?) {

            }
        })

        binding.searchPlace.setOnClickListener{
            Constants().touch_vibrate(applicationContext)
            val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()
            val fields: List<Place.Field> = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG)
            val intent: Intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .setCountry(user!!.phone.country_name_code)
                .build(this)
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        }

        binding.findMeCardview.setOnClickListener {
            auto_adjust_to_my_loc = false
            binding.locateIcon.setImageResource(R.drawable.locate_icon)
            move_cam_to_my_location()
        }

        binding.findMeCardview.setOnLongClickListener {
            if(!auto_adjust_to_my_loc){
                Toast.makeText(applicationContext,"Auto-location on",Toast.LENGTH_SHORT).show()
                auto_adjust_to_my_loc = true
                binding.locateIcon.setImageResource(R.drawable.locate_icon_green)
            }else{
                Toast.makeText(applicationContext,"Auto-location off",Toast.LENGTH_SHORT).show()
                auto_adjust_to_my_loc = false
                binding.locateIcon.setImageResource(R.drawable.locate_icon)
            }

            true
        }

        load_active_route_on_map()
//        show_all_markers()

        onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                Log.e("MapActivity", "Place: " + place.name+" and latlng: "+place.latLng!!.latitude)

//                if(place.name!=null)selectedAreaDescription = place.name.toString()
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latLng!!.latitude, place.latLng!!.longitude),
                    mMap.cameraPosition.zoom))
                whenNetworkAvailable()
            }
            else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                val status = Autocomplete.getStatusFromIntent(data!!)
                Log.e("MapsActivity", status.statusMessage.toString())
                whenNetworkLost()
            }
            else if (resultCode == Activity.RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }

    }

    fun openRouteCreater(organisation: organisation){
        binding.finishCreateRoute.visibility = View.GONE
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        if((organisation.admins!=null && organisation.admins.admins.contains(uid)) || uid.equals(Constants().pass)) {
            binding.finishCreateLayout.visibility = View.VISIBLE
            binding.addStopLayout.visibility = View.VISIBLE
            binding.setEndingLayout.visibility = View.VISIBLE
            binding.setStartingLayout.visibility = View.VISIBLE
        }
        else{
            binding.finishCreateLayout.visibility = View.GONE
            binding.addStopLayout.visibility = View.GONE
            binding.setEndingLayout.visibility = View.GONE
            binding.setStartingLayout.visibility = View.GONE
        }

        hide_normal_home_items()
        if(viewing_route!=null){
            load_my_route(viewing_route!!)
        }
        is_creating_routes = true
        load_bus_stop_adapter()

        binding.setStartingLayout.setOnClickListener {
            if(is_picking_destination_location || is_picking_stop_location) onBackPressed()
            open_location_picker()
            if(is_picking_source_location){
                Constants().touch_vibrate(applicationContext)
                set_start_pos = mMap.getCameraPosition().target
                getLocationDescription(set_start_pos!!,binding.startLocationProgressbar,binding.startLocationDescription)
                onBackPressed()
                add_marker(set_start_pos!!,constants.start_loc, constants.start_loc)
                binding.endingLocationPart.visibility = View.VISIBLE

                if(set_end_pos!=null){
                    //both are set, draw the route
                    binding.createRouteLayout.performClick()
                }
                if(set_start_pos!=null && set_end_pos!=null){
                    binding.finishCreateRoute.visibility = View.VISIBLE
                }
            }else{
                is_picking_source_location = true
                binding.setStartLocationTextview.text = getString(R.string.done)
                binding.setStartLocationIcon.setImageResource(R.drawable.check_icon)
                when_back_pressed_from_setting_location = {
                    binding.setStartLocationTextview.text = getString(R.string.set_location)
                    binding.setStartLocationIcon.setImageResource(R.drawable.down_arrow)
                    is_picking_source_location = false
                    when_back_pressed_from_setting_location = {}
                }
            }
        }

        binding.setEndingLayout.setOnClickListener {
            if(is_picking_source_location || is_picking_stop_location) onBackPressed()
            open_location_picker()
            if(is_picking_destination_location){
                Constants().touch_vibrate(applicationContext)
                set_end_pos = mMap.getCameraPosition().target
                getLocationDescription(set_end_pos!!,binding.endLocationProgressbar,binding.endLocationDescription)
                onBackPressed()
                add_marker(set_end_pos!!,constants.end_loc, constants.end_loc)
                show_all_markers()
                binding.stopsLocationPart.visibility = View.VISIBLE
                if(set_start_pos!=null){
                    //both are set, draw the route
                    binding.createRouteLayout.performClick()
                }
                if(set_start_pos!=null && set_end_pos!=null){
                    binding.finishCreateRoute.visibility = View.VISIBLE
                }
            }else{
                is_picking_destination_location = true
                binding.setEndLocationTextview.text = getString(R.string.done)
                binding.setEndLocationIcon.setImageResource(R.drawable.check_icon)
                when_back_pressed_from_setting_location = {
                    binding.setEndLocationTextview.text = getString(R.string.set_location)
                    binding.setEndLocationIcon.setImageResource(R.drawable.down_arrow)
                    is_picking_destination_location = false
                    when_back_pressed_from_setting_location = {}
                }
            }

        }

        binding.addStopLayout.setOnClickListener {
            if(is_picking_source_location || is_picking_destination_location) onBackPressed()
            open_location_picker()
            if(is_picking_stop_location){
                Constants().touch_vibrate(applicationContext)
                val stop_pos = mMap.cameraPosition.target
                onBackPressed()

                var bus_stop = bus_stop(Calendar.getInstance().timeInMillis, stop_pos)
                added_bus_stops.add(bus_stop)
                load_bus_stop_adapter()

                add_marker(stop_pos,constants.stop_loc,bus_stop.creation_time.toString())
                show_all_markers()

                if(set_start_pos!=null && set_end_pos!=null){
                    //both are set, draw the route
                    binding.createRouteLayout.performClick()
                }
            }else{
                is_picking_stop_location = true
                binding.setBusStopTextview.text = getString(R.string.done)
                binding.addRouteIcon.setImageResource(R.drawable.check_icon)
                when_back_pressed_from_setting_location = {
                    binding.setBusStopTextview.text = getString(R.string.add_a_stop)
                    binding.addRouteIcon.setImageResource(R.drawable.add_icon)
                    is_picking_stop_location = false
                    when_back_pressed_from_setting_location = {}
                }
            }
        }

        binding.startLocationDescription.setOnClickListener{
            if(set_start_pos!=null){
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(set_start_pos!!.latitude,
                    set_start_pos!!.longitude), mMap.cameraPosition.zoom))
            }
        }

        binding.endLocationDescription.setOnClickListener {
            if(set_end_pos!=null){
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(set_end_pos!!.latitude,
                    set_end_pos!!.longitude), mMap.cameraPosition.zoom))
            }
        }

        binding.busStopsTextview.setOnClickListener {
            selected_stop = ""
            load_bus_stop_adapter()
            show_all_markers()
        }

        binding.startingLocationPin.setOnClickListener {
            binding.startLocationDescription.performClick()
        }

        binding.endingLocationPin.setOnClickListener {
            binding.endLocationDescription.performClick()
        }

        binding.stopLocationPin.setOnClickListener {
            binding.busStopsTextview.performClick()
        }

        binding.createRouteLayout.setOnClickListener {
            constants.touch_vibrate(applicationContext)
            getLocationsRoute()
            binding.finishCreateRoute.visibility = View.VISIBLE
        }

        binding.finishCreateRoute.setOnClickListener {
            constants.touch_vibrate(applicationContext)
            if(viewing_route!=null){
                update_route(viewing_route!!,organisation)
            }else{
                create_route(organisation)
            }
        }

        if(set_start_pos!=null){
            binding.endingLocationPart.visibility = View.VISIBLE
        }else{
            binding.endingLocationPart.visibility = View.GONE
            binding.stopsLocationPart.visibility = View.GONE
        }

        if(set_end_pos!=null){
            binding.stopsLocationPart.visibility = View.VISIBLE
        }else{
            binding.stopsLocationPart.visibility = View.GONE
        }

        if(set_end_pos!=null && set_start_pos!=null){
            binding.finishCreateRoute.visibility = View.VISIBLE
        }else{
            binding.finishCreateRoute.visibility = View.GONE
        }

        show_all_markers()
    }

    var is_location_picker_open = false
    var is_picking_source_location = false
    var is_picking_destination_location = false
    var is_picking_stop_location = false
    var when_back_pressed_from_setting_location: () -> Unit = {}

    fun open_location_picker(){
        binding.setLocationPin.visibility = View.VISIBLE
        binding.searchPlace.visibility = View.VISIBLE
        is_location_picker_open = true

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, ZOOM_FOCUSED))
        binding.finishCreateRoute.visibility = View.GONE

        can_update_my_location = false
        remove_my_location_on_map()
    }

    fun close_location_picker(){
        binding.setLocationPin.visibility = View.GONE
        binding.searchPlace.visibility = View.GONE
        is_location_picker_open = false
        when_back_pressed_from_setting_location()
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, ZOOM))

        if(set_start_pos!=null && set_end_pos!=null){
            binding.finishCreateRoute.visibility = View.VISIBLE
        }
        load_my_location_on_map()
        show_all_markers()
    }

    override fun onMyLocationButtonClick(): Boolean {
        setUsersLastLocation()
        Constants().touch_vibrate(applicationContext)
        return true
    }

    fun setUsersLastLocation(){
        val mLastLocation = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(mLastLocation.latitude, mLastLocation.longitude),
            mMap.cameraPosition.zoom))
    }

    fun move_cam_to_my_location(){
        if(mLastKnownLocations.isNotEmpty()) {
            val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
            val ll = LatLng(last_loc.latitude, last_loc.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(ll.latitude, ll.longitude), mMap.cameraPosition.zoom))
        }else{
            set_up_getting_my_location()
        }
    }




    val added_markers: HashMap<String,Marker> = HashMap()
    fun add_marker(lat_lng:LatLng, type: String, name: String){
        var op = MarkerOptions().position(lat_lng)
        var final_icon: BitmapDrawable?  = null

        if(type.equals(constants.start_loc)){
            val icon = getDrawable(R.drawable.starting_location_pin) as BitmapDrawable
            final_icon = icon
        }
        else if(type.equals(constants.end_loc)){
            val icon = getDrawable(R.drawable.ending_location_pin) as BitmapDrawable
            final_icon = icon
        }
        else if(type.equals(constants.stop_loc)){
            val icon = getDrawable(R.drawable.stop_location_pin) as BitmapDrawable
            final_icon = icon
//            op.draggable(true)
        }

        val height = 77
        val width = 30
        if(final_icon!=null) {
            val b: Bitmap = final_icon.bitmap
            val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
            op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
        }

        if(added_markers.containsKey(name)){
            added_markers.get(name)!!.remove()
        }
        val new_marker = mMap.addMarker(op)
        added_markers.put(name, new_marker)
    }

    fun remove_marker(name: String){
        if(added_markers.containsKey(name)){
            added_markers.get(name)!!.remove()
            added_markers.remove(name)
        }
    }

    fun update_marker(new_marker: LatLng){
        if(set_start_pos!=null && set_start_pos!!.latitude.equals(new_marker.latitude) &&
            set_start_pos!!.longitude.equals(new_marker.longitude)){
            set_start_pos = new_marker
        }

        if(set_end_pos!=null && set_end_pos!!.latitude.equals(new_marker.latitude) &&
            set_end_pos!!.longitude.equals(new_marker.longitude)){
            set_end_pos = new_marker
        }

        for(item in added_bus_stops){
            if(item.stop_location.latitude.equals(new_marker.latitude) &&
                item.stop_location.longitude.equals(new_marker.longitude)){
                item.stop_location = new_marker
                load_bus_stop_adapter()
                break
            }
        }

    }

    fun show_all_markers(){
        if(added_markers.values.isNotEmpty()) {
            val builder: LatLngBounds.Builder = LatLngBounds.Builder()
            for (marker in added_markers.values) {
                builder.include(marker.position)
            }
            val bounds = builder.build()
            val padding = dpToPx(130)
            val cu: CameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mMap.animateCamera(cu)
        }
    }

    var selected_stop = ""
    fun load_bus_stop_adapter(){
        binding.routeStopsRecyclerview.adapter = newBusStopsListAdapter()
        binding.routeStopsRecyclerview.layoutManager = LinearLayoutManager(baseContext,
            LinearLayoutManager.HORIZONTAL,false)

        if(added_bus_stops.isNotEmpty()){
            binding.routeStopsRecyclerview.visibility = View.VISIBLE
        }else{
            binding.routeStopsRecyclerview.visibility = View.GONE
        }
    }

    internal inner class newBusStopsListAdapter : RecyclerView.Adapter<ViewHolderBusStop>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolderBusStop {
            val vh = ViewHolderBusStop(LayoutInflater.from(baseContext)
                .inflate(R.layout.recycler_item_new_stop, viewGroup, false))
            return vh
        }

        override fun onBindViewHolder(v: ViewHolderBusStop, position: Int) {
            var stop = added_bus_stops.get(position)

            v.image.setOnClickListener {
                if(selected_stop.equals(stop.creation_time.toString())){
                    added_bus_stops.removeAt(position)
                    remove_marker(selected_stop)

                    if(set_start_pos!=null && set_end_pos!=null){
                        //both are set, draw the route
                        binding.createRouteLayout.performClick()
                    }

                }else{
                    val uid = FirebaseAuth.getInstance().currentUser!!.uid
                    if((get_active_org()!=null && get_active_org()!!.admins!=null && get_active_org()!!.admins.admins.contains(uid)) ||
                        uid.equals(Constants().pass)) {
                        v.delete_icon.visibility = View.VISIBLE
                        selected_stop = stop.creation_time.toString()
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(stop.stop_location.latitude,
                        stop.stop_location.longitude), mMap.cameraPosition.zoom))

                }
                load_bus_stop_adapter()
            }

            if(selected_stop.equals(stop.creation_time.toString())){
                v.delete_icon.visibility = View.VISIBLE

            }else{
                v.delete_icon.visibility = View.GONE
            }
        }

        override fun getItemCount():Int {
            return added_bus_stops.size
        }

    }

    internal inner class ViewHolderBusStop (view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val delete_icon: ImageView = view.findViewById(R.id.delete_icon)
    }




    fun hide_normal_home_items(){
        binding.settings.visibility = View.GONE
        binding.bottomSheetLayout.visibility = View.GONE
        binding.money.visibility = View.GONE
        binding.newRouteView.visibility = View.VISIBLE

        remove_active_route_on_map()
    }

    fun show_normal_home_items(){
        binding.settings.visibility = View.VISIBLE
        binding.bottomSheetLayout.visibility = View.VISIBLE
        binding.money.visibility = View.VISIBLE
        binding.newRouteView.visibility = View.GONE
        binding.finishCreateRoute.visibility = View.GONE

        load_active_route_on_map()
    }

    override fun onMyLocationClick(p0: Location) {

    }

    fun when_location_gotten(){
        Log.e("when_location_gotten","when location gotten")
        val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
        val ll = LatLng(last_loc.latitude,last_loc.longitude)
        update_notification()

        if(can_update_my_location)load_my_location_on_map()
        if(can_share_location){
            set_location_data_in_firebase(ll)
            if(mLastKnownLocations.size%2==0){
                //every three updates
                addPulsatingEffect(LatLng(last_loc.latitude,last_loc.longitude))
            }
        }
        if(auto_adjust_to_my_loc) {
            Log.e("when_location_gotten","adjusting to my location")
            move_cam_to_my_location()
        }

        if(get_active_route()!=null && constants.SharedPreferenceManager(applicationContext).can_auto_swapp_route()){
            if(distance_to(ll, get_active_route()!!.set_end_pos!!) <= constants.distance_threshold){
                active_route = get_mirror_route(get_active_route()!!).route_id
                if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation).when_route_picked(active_route)
                }
                if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes).when_route_picked(active_route)
                }
                when_active_route_set()
                load_active_route_on_map()
            }
        }
    }

    fun get_mirror_route(route: route): route{
        for(item in routes){
            if(!item.route_id.equals(route.route_id)){
                val start_end = Constants().distance_km(item.set_start_pos!!.latitude,item.set_start_pos!!.longitude,
                    route.set_end_pos!!.latitude,route.set_end_pos!!.longitude)
                Log.e(TAG, "start_end: ${start_end}")

                val end_start =  Constants().distance_km(route.set_start_pos!!.latitude,route.set_start_pos!!.longitude,
                    item.set_end_pos!!.latitude,item.set_end_pos!!.longitude)
                Log.e(TAG, "end_start: ${end_start}")

                if(start_end<=Constants().closeness_limit && end_start<=Constants().closeness_limit){
                    return item
                }
            }
        }
        return route
    }

    fun getLocationDescription(lat_lng: LatLng, progress_view: ProgressBar, textview: TextView){
        progress_view.visibility = View.VISIBLE
        val https: String = "https://maps.googleapis.com/maps/api/geocode/json?latlng="+lat_lng.latitude+","+lat_lng.longitude+"&key="+ Apis().geocoder_api_key
        Log.e("MapsActivity","Attempting to find location description from geo-coordinates...")

        val queue = Volley.newRequestQueue(this)
        val stringRequest = JsonObjectRequest(Request.Method.GET, https,null, Response.Listener
        { response ->
            whenNetworkAvailable()
            val gson = Gson()
            val geoData = gson.fromJson(response.toString(), geo_data.reverseGeoData::class.java)
            Log.e("MapsActivity", "Data parsed: "+geoData.status)

            val results = geoData.results
            var selectedString = ""
            var selected_location_id = ""

            for (result in results){
                val area_name = result.formatted_address
                if(area_name.length>selectedString.length){
                    selectedString = area_name
                    selected_location_id = result.place_id
                }
            }
            if(!selectedString.equals("")){
                Log.e("MapsActivity", "Selected Name:"+selectedString)
                if(textview.equals(binding.startLocationDescription)){
                    set_start_pos_desc = selectedString
                    set_start_pos_id = selected_location_id
                    start_pos_geo_data = geoData
                }else if(textview.equals(binding.endLocationDescription)){
                    set_end_pos_desc = selectedString
                    set_end_pos_id = selected_location_id
                    end_pos_geo_data = geoData
                }
                textview.text = selectedString
            }
            progress_view.visibility = View.GONE

        }, Response.ErrorListener {
            Log.e("MapsActivity","It didnt work! "+it.message.toString())
            whenNetworkLost()
        })

        queue.add(stringRequest)
    }

    fun getLocationsRoute(){
        showLoadingScreen()
        var source = set_start_pos!!
        var destination = set_end_pos!!
        var waypoints = ""

        for(item in added_bus_stops){
            if(added_bus_stops.get(added_bus_stops.lastIndex).creation_time == item.creation_time){
                //if item is last
                waypoints = waypoints+"via:${item.stop_location.latitude}%2C${item.stop_location.longitude}"
            }else{
                waypoints = waypoints+"via:${item.stop_location.latitude}%2C${item.stop_location.longitude}%7C"
            }
        }

        var https = "https://maps.googleapis.com/maps/api/directions/json?origin=${source.latitude},${source.longitude}&destination=${destination.latitude},${destination.longitude}&key=${Apis().directions_api}"
        if(!waypoints.equals("")){
            https = "https://maps.googleapis.com/maps/api/directions/json?origin=${source.latitude},${source.longitude}&destination=${destination.latitude},${destination.longitude}&waypoints=${waypoints}&key=${Apis().directions_api}"
        }

        val queue = Volley.newRequestQueue(this)
        val stringRequest = JsonObjectRequest(Request.Method.GET, https,null, Response.Listener
        { response ->
            val directionsData = Gson().fromJson(response.toString(), directions_data::class.java)
            Log.e("MapsActivity", "Data parsed: "+directionsData.status)

            val entire_path: MutableList<List<LatLng>> = ArrayList()
            if(directionsData.routes.isNotEmpty()){
                val route = directionsData.routes[0]
                for(leg in route.legs){
//                    Log.e(TAG, "leg start adress: ${leg.start_address}")
                    for(step in leg.steps){
//                        Log.e(TAG,"step maneuver: ${step.maneuver}")
                        val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                        entire_path.add(pathh)
                    }
                }
            }
            remove_drawn_route()
            draw_route(entire_path)
            route_directions_data = directionsData
            hideLoadingScreen()
        }, Response.ErrorListener {
            Log.e("MapsActivity","It didnt work! "+it.message.toString())
            whenNetworkLost()
        })

        queue.add(stringRequest)

    }




    fun draw_route(entire_paths: MutableList<List<LatLng>>){
        remove_drawn_route()
        for (i in 0 until entire_paths.size) {
            if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                val op = PolylineOptions()
                    .addAll(entire_paths[i])
                    .width(5f)
                    .color(Color.WHITE)
                drawn_polyline.add(mMap.addPolyline(op))
            }else{
                val op = PolylineOptions()
                    .addAll(entire_paths[i])
                    .width(5f)
                    .color(Color.BLACK)
                drawn_polyline.add(mMap.addPolyline(op))
            }

        }

    }

    fun remove_drawn_route(){
        if(drawn_polyline.isNotEmpty()){
            for(item in drawn_polyline){
                item.remove()
            }
            drawn_polyline.clear()
        }
    }

    fun remove_all_added_pins(){
        for(item in added_markers.values){
            item.remove()
        }
        added_markers.clear()
    }

    fun when_location_marker_clicked(its_pos: LatLng){
        val new_lat_lng = LatLng(its_pos.latitude, its_pos.longitude)
        val zoom = mMap.cameraPosition.zoom

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new_lat_lng, zoom))
        for(item in added_bus_stops){
            if(item.stop_location.latitude.equals(new_lat_lng.latitude) && item.stop_location.longitude.equals(new_lat_lng.longitude)){
                selected_stop = item.creation_time.toString()
                load_bus_stop_adapter()
            }
        }
    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        when_location_marker_clicked(p0!!.position)
        return true
    }





    fun create_route(organisation: organisation){
        showLoadingScreen()

        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val route_ref = db.collection(constants.organisations)
            .document(organisation.country!!)
            .collection(constants.country_routes)
            .document()

        val time = Calendar.getInstance().timeInMillis
        val new_route = route(time,organisation.org_id!!)

        new_route.starting_pos_desc = set_start_pos_desc
        new_route.ending_pos_desc = set_end_pos_desc

        new_route.set_start_pos = set_start_pos
        new_route.set_end_pos = set_end_pos

        new_route.start_pos_geo_data = start_pos_geo_data
        new_route.end_pos_geo_data = end_pos_geo_data

        new_route.added_bus_stops = added_bus_stops
        new_route.route_directions_data = route_directions_data

        new_route.creater = uid
        new_route.country = organisation.country!!
        new_route.route_id = route_ref.id

        val data = hashMapOf(
            "organisation_id" to organisation.org_id,
            "route" to Gson().toJson(new_route),
            "country" to organisation.country,
            "route_id" to route_ref.id,
            "creation_time" to time,
            "creater" to uid
        )

        route_ref.set(data).addOnSuccessListener {
            hideLoadingScreen()
            routes.add(new_route)
            if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                    .when_route_data_updated(load_my_organisations_routes(organisation))
            }

            if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
                (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes)
                    .when_route_data_updated(load_my_organisations_routes(organisation))
            }

            //remove all the markers on the map
            for(item in added_markers.values){
                item.remove()
            }
            //remove the drawn route
            remove_drawn_route()
            binding.finishCreateRoute.visibility = View.GONE
            onBackPressed()
            Toast.makeText(applicationContext,"Done!", Toast.LENGTH_SHORT).show()
        }


    }

    fun update_route(route: route, organisation: organisation){
        showLoadingScreen()

        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val route_ref = db.collection(constants.organisations)
            .document(organisation.country!!)
            .collection(constants.country_routes)
            .document(route.route_id)

        route.starting_pos_desc = set_start_pos_desc
        route.ending_pos_desc = set_end_pos_desc

        route.set_start_pos = set_start_pos
        route.set_end_pos = set_end_pos

        route.start_pos_geo_data = start_pos_geo_data
        route.end_pos_geo_data = end_pos_geo_data

        route.added_bus_stops = added_bus_stops
        route.route_directions_data = route_directions_data

        route.creater = uid
        route.country = organisation.country!!
        route.route_id = route_ref.id

        val data = hashMapOf(
            "organisation_id" to organisation.org_id,
            "route" to Gson().toJson(route),
            "country" to organisation.country,
            "route_id" to route.route_id,
            "creation_time" to route.creation_time,
            "creater" to route.creater
        )

        route_ref.set(data).addOnSuccessListener {
            hideLoadingScreen()
            var pos = -1
            for(item in routes){
                if(item.route_id.equals(route.route_id)){
                    pos = routes.indexOf(item)
                }
            }
            if(pos!=-1){
                routes.removeAt(pos)
                routes.add(route)
            }
            if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                    .when_route_data_updated(load_my_organisations_routes(organisation))
            }

            if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
                (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes)
                    .when_route_data_updated(load_my_organisations_routes(organisation))
            }

            //remove all the markers on the map
            for(item in added_markers.values){
                item.remove()
            }
//            viewRoute(route, organisation)
            //remove the drawn route
            remove_drawn_route()
            onBackPressed()
            Toast.makeText(applicationContext,"Done!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            hideLoadingScreen()
            Toast.makeText(applicationContext,"Something went wrong", Toast.LENGTH_SHORT).show()
        }

    }

    fun delete_route(route: route, organisation: organisation){
        showLoadingScreen()

        val route_ref = db.collection(constants.organisations)
            .document(organisation.country!!)
            .collection(constants.country_routes)
            .document(organisation.org_id!!)
            .collection(constants.routes)
            .document(route.route_id)

        route_ref.delete().addOnSuccessListener {
            hideLoadingScreen()
            var pos = -1
            for(item in routes){
                if(item.route_id.equals(route.route_id)){
                    pos = routes.indexOf(item)
                }
            }
            if(pos!=-1){
                routes.removeAt(pos)
            }
            if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                    .when_route_data_updated(load_my_organisations_routes(organisation))
            }

            //remove all the markers on the map
            for(item in added_markers.values){
                item.remove()
            }
            //remove the drawn route
            remove_drawn_route()
            onBackPressed()
            Toast.makeText(applicationContext,"Done!", Toast.LENGTH_SHORT).show()
        }
    }

    fun load_my_route(route: route){
        binding.startingLocationPart.visibility = View.VISIBLE
        binding.endingLocationPart.visibility = View.VISIBLE
        binding.stopsLocationPart.visibility = View.VISIBLE
        binding.finishCreateRoute.visibility = View.VISIBLE

        set_start_pos = route.set_start_pos
        set_start_pos_desc = route.starting_pos_desc
        binding.startLocationDescription.text = set_start_pos_desc


        var selectedString = ""
        var selected_location_id = ""
        for (result in route.start_pos_geo_data!!.results){
            val area_name = result.formatted_address
            if(area_name.length>selectedString.length){
                selected_location_id = result.place_id
            }
        }

        set_start_pos_id = selected_location_id
        start_pos_geo_data = route.start_pos_geo_data

        set_end_pos = route.set_end_pos
        set_end_pos_desc = route.ending_pos_desc
        binding.endLocationDescription.text = set_end_pos_desc

        selectedString = ""
        selected_location_id = ""
        for (result in route.end_pos_geo_data!!.results){
            val area_name = result.formatted_address
            if(area_name.length>selectedString.length){
                selected_location_id = result.place_id
            }
        }

        set_end_pos_id = selected_location_id
        end_pos_geo_data = route.end_pos_geo_data
        added_bus_stops = route.added_bus_stops
        route_directions_data = route.route_directions_data

        val entire_path: MutableList<List<LatLng>> = ArrayList()
        if(route_directions_data!!.routes.isNotEmpty()){
            val route = route_directions_data!!.routes[0]
            for(leg in route.legs){
//                Log.e(TAG, "leg start adress: ${leg.start_address}")
                for(step in leg.steps){
//                    Log.e(TAG,"step maneuver: ${step.maneuver}")
                    val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                    entire_path.add(pathh)
                }
            }
        }

        draw_route(entire_path)
        add_marker(set_start_pos!!,constants.start_loc, constants.start_loc)
        add_marker(set_end_pos!!,constants.end_loc, constants.end_loc)
        for(item in added_bus_stops){
            add_marker(item.stop_location,constants.stop_loc, item.creation_time.toString())
        }

    }

    fun load_my_organisations_routes(organisation: organisation): ArrayList<route>{
        val my_routes: ArrayList<route> = ArrayList()
        for(item in routes){
            if(item.org_id.equals(organisation.org_id)){
                my_routes.add(item)
            }
        }

        return my_routes
    }





    override fun whenSetRoute(route: route, organisation: organisation) {
        active_route = route.route_id
        onBackPressed()
        Constants().SharedPreferenceManager(applicationContext).auto_swapp_route(false)
        if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
            (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation).when_route_picked(active_route)
        }
        if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
            (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes).when_route_picked(active_route)
        }
        when_active_route_set()
        load_active_route_on_map()
        Toast.makeText(applicationContext,"Done!", Toast.LENGTH_SHORT).show()
    }

    var is_edit_route_opened = false
    override fun whenEditRoute(route: route, organisation: organisation) {
        remove_all_added_pins()
        remove_drawn_route()

        added_bus_stops.clear()
        set_start_pos = null
        set_start_pos_desc = ""
        binding.startLocationDescription.text = ""
        set_start_pos_id = ""
        start_pos_geo_data= null

        set_end_pos= null
        set_end_pos_desc = ""
        set_end_pos_id = ""
        binding.endLocationDescription.text = ""
        end_pos_geo_data= null
        drawn_polyline.clear()
        route_directions_data = null

        binding.setBusStopTextview.text = getString(R.string.add_a_stop)
        binding.addRouteIcon.setImageResource(R.drawable.add_icon)
        is_picking_stop_location = false
        is_picking_source_location = false
        is_picking_destination_location = false

        viewing_route = route
        onBackPressed()
        openRouteCreater(organisation)
        is_edit_route_opened = true
    }

    override fun whenDisableRoute(is_disabled: Boolean, route: route, organ: organisation) {
        showLoadingScreen()
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        db.collection(constants.organisations)
            .document(organ.country!!)
            .collection(constants.country_routes)
            .document(route.route_id)
            .update(mapOf(
                "disabled" to is_disabled
            )).addOnSuccessListener {
                hideLoadingScreen()

                for(item in routes){
                    if(item.route_id.equals(route.route_id)){
                        item.disabled = is_disabled
                    }
                }
                if(supportFragmentManager.findFragmentByTag(_view_all_routes)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_all_routes) as ViewAllRoutes)
                        .when_route_data_updated(load_my_organisations_routes(organ))
                }
                if(supportFragmentManager.findFragmentByTag(_view_route)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_route) as ViewRoute)
                        .when_route_disabled(is_disabled)
                }
                if(supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation)
                        .when_route_disabled(is_disabled, route)
                }
            }.addOnFailureListener {
                hideLoadingScreen()
                whenNetworkAvailable()

                if(supportFragmentManager.findFragmentByTag(_view_route)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_route) as ViewRoute)
                        .when_route_disabled(!is_disabled)
                }
            }
    }

    fun get_active_org(): organisation?{
        for(item in organisations){
            if (item.org_id.equals(active_organisation)){
                return item
            }
        }
        return null
    }

    fun get_active_route(): route?{
        for(item in routes){
            if(item.route_id.equals(active_route)){
                return item
            }
        }
        return null
    }

    fun when_active_route_set(){
        if(get_active_route()!=null){
            //active route and org probably
            binding.continueLayout.visibility = View.GONE
            binding.viewLayout.visibility = View.VISIBLE
            binding.sharingLocationLayout.visibility = View.VISIBLE
            val active_route = get_active_route()!!

            binding.title.text = "Route"
            binding.destinationTextview.text = "To: ${active_route.ending_pos_desc}"
            store_session_data()

            show_all_markers()
            get_route_views_first(active_route)
        }
    }

    fun when_active_org_set(){
        if(get_active_org()!=null){
            binding.continueLayout.visibility = View.VISIBLE
            binding.viewLayout.visibility = View.GONE
            binding.sharingLocationLayout.visibility = View.GONE
            binding.title.text = "Set a route"
            binding.destinationTextview.text = "Set a route you want to use"
            store_session_data()
        }
    }

    fun load_active_route_on_map(){
        remove_active_route_on_map()

        if(get_active_route()!=null) {
            val entire_path: MutableList<List<LatLng>> = ArrayList()
            if (get_active_route()!!.route_directions_data!!.routes.isNotEmpty()) {
                val route = get_active_route()!!.route_directions_data!!.routes[0]
                for (leg in route.legs) {
//                    Log.e(TAG, "leg start adress: ${leg.start_address}")
                    for (step in leg.steps) {
//                        Log.e(TAG, "step maneuver: ${step.maneuver}")
                        val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                        entire_path.add(pathh)
                    }
                }
            }
            draw_route(entire_path)
            add_marker(get_active_route()!!.set_start_pos!!, constants.start_loc, constants.start_loc)
            add_marker(get_active_route()!!.set_end_pos!!, constants.end_loc, constants.end_loc)
            for (item in get_active_route()!!.added_bus_stops) {
                add_marker(item.stop_location, constants.stop_loc, item.creation_time.toString())
            }
            Handler().postDelayed({ show_all_markers() }, 500)

//            val new_lat_lng = LatLng(get_active_route()!!.set_start_pos!!.latitude, get_active_route()!!.set_start_pos!!.longitude)
//            val zoom = mMap.cameraPosition.zoom
//
//            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new_lat_lng, zoom))
        }
    }

    fun remove_active_route_on_map(){
        remove_all_added_pins()
        remove_drawn_route()
    }





    var can_update_my_location = true
    fun load_my_location_on_map(){
        val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)

        var lat_lng = LatLng(last_loc.latitude,last_loc.longitude)
        if(get_active_route()!=null){
            lat_lng = get_closest_point_to_route(get_active_route()!!, lat_lng)
        }

        var op = MarkerOptions().position(lat_lng)
        var final_icon: BitmapDrawable?  = getDrawable(R.drawable.bus_loc) as BitmapDrawable

        val height = 108
        val width = 55
        if(final_icon!=null) {
            val b: Bitmap = final_icon.bitmap
            val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
            op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
        }

        if(my_marker!=null){
            my_marker!!.remove()
        }
        my_marker = mMap.addMarker(op)


        for(trail in my_marker_trailing_markers){
            trail.remove()
        }
        my_marker_trailing_markers.clear()

        for(item in mLastKnownLocations){
            val pos = mLastKnownLocations.indexOf(item)
            if(pos+3 >= mLastKnownLocations.lastIndex){
                //item is one of the last three

                var loc = LatLng(item.latitude,item.longitude)
                if(get_active_route()!=null){
                    loc = get_closest_point_to_route(get_active_route()!!, loc)
                }
                val circleOptions = CircleOptions()
                circleOptions.center(loc)
                circleOptions.radius(1.0)
                if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    circleOptions.fillColor(Color.DKGRAY)
                }else{
                    circleOptions.fillColor(Color.BLACK)
                }
                circleOptions.strokeColor(Color.LTGRAY)
                circleOptions.strokeWidth(0f)
                val circle = mMap.addCircle(circleOptions)
                my_marker_trailing_markers.add(circle)
            }
        }
    }

    fun move_camera_to_my_location(){
        if(mLastKnownLocations.isNotEmpty()) {
            val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
            move_camera(LatLng(last_loc.latitude, last_loc.longitude))
        }
    }

    fun move_camera(pos: LatLng){
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, mMap.cameraPosition.zoom))
    }

    fun remove_my_location_on_map(){
        for(trail in my_marker_trailing_markers){
            trail.remove()
        }
        my_marker_trailing_markers.clear()

        if(my_marker!=null){
            my_marker!!.remove()
            my_marker == null
        }
    }

    fun get_closest_point_to_route(set_route :route, my_location:LatLng) :LatLng {
        val entire_path: MutableList<List<LatLng>> = ArrayList()
        if (set_route.route_directions_data!!.routes.isNotEmpty()) {
            val route = set_route.route_directions_data!!.routes[0]
            for (leg in route.legs) {
//                Log.e(TAG, "leg start adress: ${leg.start_address}")
                for (step in leg.steps) {
//                    Log.e(TAG, "step maneuver: ${step.maneuver}")
                    val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                    entire_path.add(pathh)
                }
            }
        }

        var closest_point = entire_path[0][0]
        var its_distance_to_me = distance_to(closest_point, my_location)
        for(path in entire_path){
            for(point in path){
                if(distance_to(point, my_location)<its_distance_to_me){
                    //this point is shorter
                    closest_point = point
                    its_distance_to_me = distance_to(point, my_location)
                }
            }
        }

        if(its_distance_to_me<constants.distance_threshold){
            //if my location is too far from any point to the route
            return closest_point
        }else{
            return my_location
        }
    }

    fun distance_to(lat_lng: LatLng, other_lat_lng: LatLng): Long{
        val loc1 = Location("")
        loc1.latitude = lat_lng.latitude
        loc1.longitude = lat_lng.longitude

        val loc2 = Location("")
        loc2.latitude = other_lat_lng.latitude
        loc2.longitude = other_lat_lng.longitude

        val distanceInMeters = loc1.distanceTo(loc2)
        return distanceInMeters.toLong()
    }

    override fun onSettingsSwitchNightMode() {

    }

    override fun onSettingsChangeOrganisation() {
        val orgs = Gson().toJson(organisation.organisation_list(organisations))
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id,JoinOrganisation.newInstance("","", orgs),_join_organisation).commit()
    }

    override fun onSettingsSignIn() {
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id, SignUp.newInstance("",""),_sign_up).commit()
    }


    val CHANNEL_ID = "matt_notif"
    val ACTION_SNOOZE = "ACTION_SNOOZE"
    val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    val notificationId = 44

    var builder: NotificationCompat.Builder? = null
    fun load_notification(is_silent: Boolean){
//        remove_notification()

        builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_icon)
            .setContentTitle("Matt Driver")
            .setContentText("Your location is being shared.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
//             .setContentIntent(pendingIntent)
//             .addAction(R.drawable.cancel_icon, getString(R.string.stop), pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        if(is_silent){
            builder!!.setNotificationSilent()
        }

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(notificationId, builder!!.build())
        }

    }

    fun update_notification(){
//        if(builder!=null) {
//            with(NotificationManagerCompat.from(this)) {
//                // notificationId is a unique int for each notification that you must define
//                notify(notificationId, builder!!.build())
//            }
//        }
        load_notification(true)
    }

    fun remove_notification(){
        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            cancel(notificationId)
        }
        builder==null
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_name)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun set_location_data_in_firebase(pos: LatLng){
        if(get_active_org()!=null) {
            val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!

            val new_pos = db.collection(constants.organisations)
                .document(user.phone.country_name)
                .collection(constants.country_organisations)
                .document(get_active_org()!!.org_id!!)
                .collection(constants.driver_locations).document()

            val creation_time = Calendar.getInstance().timeInMillis


            val data = hashMapOf(
                "pos_id" to new_pos.id,
                "creation_time" to creation_time,
                "user" to user.uid,
                "loc" to Gson().toJson(get_closest_point_to_route(get_active_route()!!, pos)),
                "organisation" to get_active_org()!!.org_id!!,
                "route" to get_active_route()!!.route_id
            )

            new_pos.set(data).addOnSuccessListener {
                Log.e(TAG,"location updated")
            }

        }
    }

    var is_signing_in_manually = false
    var mAuth: FirebaseAuth? = null
    override fun whenSignUpDetailsSubmitted(
        email: String,
        password: String,
        name: String,
        numbr: number
    ) {
        mAuth = FirebaseAuth.getInstance()
        if(isOnline()) {
            showLoadingScreen()
            val view = this.currentFocus
            if (view != null) {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }

            is_signing_in_manually = true
            //the user is anonymous, lets link their account
            val credential = EmailAuthProvider.getCredential(email, password)
            mAuth!!.currentUser!!.linkWithCredential(credential)
                .addOnCompleteListener {task ->
                    if (task.isSuccessful) {
                        Log.d("main", "authentication successful")
                        createFirebaseUserProfile(task.result!!.user,email,name,numbr)
                    } else {
                        Snackbar.make(binding.root, resources.getString(R.string.that_didnt_work), Snackbar.LENGTH_LONG).show()
                        hideLoadingScreen()
                        is_signing_in_manually = false
                    }
                }
        }else{
            Snackbar.make(binding.root,getString(R.string.please_check_on_your_internet_connection),
                Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun createFirebaseUserProfile(user: FirebaseUser?,  email: String, name: String, numbr: number) {
        val addProfileName = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        val time = Calendar.getInstance().timeInMillis
        if (user != null) {
            user.updateProfile(addProfileName).addOnCompleteListener { task ->
                whenNetworkAvailable()
                if (task.isSuccessful) {
                    Log.d("main", "Created new username,")
                    constants.SharedPreferenceManager(applicationContext)
                        .setPersonalInfo(numbr,email,name, Calendar.getInstance().timeInMillis,user.uid)
                }
            }.addOnFailureListener { whenNetworkLost() }

            val myDataDoc = hashMapOf(
                "email" to email,
                "name" to name,
                "uid" to user.uid,
                "sign_up_time" to time,
                "user_country" to numbr.country_name
            )

            val uid = user.uid
            db.collection(constants.coll_users).document(uid)
                .set(myDataDoc)
                .addOnSuccessListener {
                    whenNetworkAvailable()
                    db.collection(constants.coll_users).document(uid).collection(constants.coll_meta_data)
                        .document(constants.doc_phone).set(numbr)
                        .addOnSuccessListener {
                            Log.e(TAG,"created a new user!")

                            hideLoadingScreen()
                            is_signing_in_manually = false

                            if(supportFragmentManager.findFragmentByTag(_settings)!=null){
                                (supportFragmentManager.findFragmentByTag(_settings) as MainSettings).didUserSignUp()
                            }
                            onBackPressed()
                        }
                }.addOnFailureListener { whenNetworkLost() }

            for(item in my_organisations){
                for(org in organisations) {
                    if(org.org_id!!.equals(item)){
                        db.collection(constants.coll_users).document(uid)
                            .collection(constants.my_organisations)
                            .document(item)
                            .set(hashMapOf(
                                "name" to org.name,
                                "org_id" to org.org_id,
                                "creation_time" to org.creation_time
                            )
                            )
                    }
                }
            }
        }
    }

    fun isOnline(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        //should check null because in airplane mode it will be null
        return netInfo != null && netInfo.isConnected
    }

    override fun whenSignInInstead() {
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(binding.money.id, SignIn.newInstance("",""),_sign_in).commit()
    }

    var mAuthListener: FirebaseAuth.AuthStateListener? = null
    override fun whenSignInDetailsSubmitted(email: String, password: String) {
        if(isOnline()){
            showLoadingScreen()
            mAuth = FirebaseAuth.getInstance()
            mAuthListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                Log.d("main", "user status changes")
                val user = firebaseAuth.currentUser
                if (user != null) {
                    val uid = user.uid
                    load_my_organisations()
                    db.collection(constants.coll_users).document(uid).get().addOnSuccessListener {
                        if(it.exists()){
                            val name = it.get("name") as String
                            val sign_up_time = it.get("sign_up_time") as Long

                            db.collection(constants.coll_users).document(uid).collection(constants.coll_meta_data)
                                .document(constants.doc_phone).get().addOnSuccessListener {
                                    val numbr = number(
                                        it.get("digit_number") as Long,
                                        it.get("country_number_code") as String,
                                        it.get("country_name") as String,
                                        it.get("country_name_code") as String
                                    )
                                    constants.SharedPreferenceManager(applicationContext).setPersonalInfo(numbr,email,name, sign_up_time,user.uid)
                                    hideLoadingScreen()
                                    if(supportFragmentManager.findFragmentByTag(_settings)!=null){
                                        (supportFragmentManager.findFragmentByTag(_settings) as MainSettings).didUserSignUp()
                                    }
                                    onBackPressed()
                                    mAuth!!.removeAuthStateListener(mAuthListener!!)
                                }

                        }
                    }
                }
            }
            mAuth!!.addAuthStateListener(mAuthListener!!)

            val view = this.currentFocus
            if (view != null) {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            mAuth!!.signInWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
                whenNetworkAvailable()
                Log.d("main", "signInWithEmail:onComplete" + task.isSuccessful)
                if (!task.isSuccessful) {
                    Snackbar.make(binding.root, "That didn't work. Please check your credentials and retry.", Snackbar.LENGTH_LONG).show()
                    if(supportFragmentManager.findFragmentByTag(_sign_in)!=null){
                        (supportFragmentManager.findFragmentByTag(_sign_in) as SignIn).didPasscodeFail()
                    }
                    hideLoadingScreen()
                }
            }.addOnFailureListener { whenNetworkLost() }
        }else{
            Snackbar.make(binding.root,getString(R.string.please_check_on_your_internet_connection),Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun whenSignInSignUpInsteadSelected() {
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(binding.money.id, SignUp.newInstance("",""),_sign_up).commit()
    }

    override fun whenDriverDeActivated(organ: organisation, drivr: driver, value: Boolean) {
        showLoadingScreen()
        val time = Calendar.getInstance().timeInMillis
        db.collection(constants.organisations)
            .document(organ.country!!)
            .collection(constants.country_organisations)
            .document(organ.org_id!!)
            .collection(constants.drivers)
            .document(drivr.driver_id)
            .set(hashMapOf(
                "driver_id" to drivr.driver_id,
                "org_id" to organ.org_id,
                "join_time" to drivr.joining_time,
                "active" to value
            ))

        if(value) {
            organ.deactivated_drivers.deactivated_drivers.add(drivr.driver_id)
        }else{
            organ.deactivated_drivers.deactivated_drivers.remove(drivr.driver_id)
        }

        db.collection(constants.organisations)
            .document(organ.country!!)
            .collection(constants.country_organisations)
            .document(organ.org_id!!)
            .update(mapOf(
                "deactives"  to Gson().toJson(organ.deactivated_drivers)
            )).addOnSuccessListener {
                hideLoadingScreen()
                Toast.makeText(applicationContext,"done!",Toast.LENGTH_SHORT).show()

                for(item in organisations){
                    if(item.org_id.equals(organ.org_id)){
                        if(value) {
                            item.deactivated_drivers.deactivated_drivers.add(drivr.driver_id)
                        }else{
                            item.deactivated_drivers.deactivated_drivers.remove(drivr.driver_id)
                        }
                    }
                }
                if(get_active_org()!=null && supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation).onOrganisationReloaded(get_active_org()!!)
                }
                if(get_active_org()!=null && supportFragmentManager.findFragmentByTag(_view_drivers)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_drivers) as Drivers).onOrganisationReloaded(get_active_org()!!)
                }
            }.addOnFailureListener {
                hideLoadingScreen()
                Toast.makeText(applicationContext,"something went wrong",Toast.LENGTH_SHORT).show()
            }
    }

    override fun whenDriverSetAdmin(organ: organisation, drivr: driver, value: Boolean) {
        showLoadingScreen()

        if(value){
            organ.admins.admins.add(drivr.driver_id)
        }else{
            organ.admins.admins.remove(drivr.driver_id)
        }


        db.collection(constants.organisations)
            .document(organ.country!!)
            .collection(constants.country_organisations)
            .document(organ.org_id!!)
            .update(mapOf(
                "admins"  to Gson().toJson(organ.admins)
            )).addOnSuccessListener {
                hideLoadingScreen()
                Toast.makeText(applicationContext,"done!",Toast.LENGTH_SHORT).show()

                for(item in organisations){
                    if(item.org_id.equals(organ.org_id)){
                        if(value){
                            item.admins.admins.add(drivr.driver_id)
                        }else{
                            item.admins.admins.remove(drivr.driver_id)
                        }
                    }
                }
                if(get_active_org()!=null && supportFragmentManager.findFragmentByTag(_view_organisation)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_organisation) as ViewOrganisation).onOrganisationReloaded(get_active_org()!!)
                }
                if(get_active_org()!=null && supportFragmentManager.findFragmentByTag(_view_drivers)!=null){
                    (supportFragmentManager.findFragmentByTag(_view_drivers) as Drivers).onOrganisationReloaded(get_active_org()!!)
                }
            }.addOnFailureListener {
                hideLoadingScreen()
                Toast.makeText(applicationContext,"something went wrong",Toast.LENGTH_SHORT).show()
            }
    }

    override fun whenViewAllRoutesReloadRoutes() {
        load_routes()
        load_organisations()
    }

    override fun whenViewAllRoutesViewRoute(route: route, organisation: organisation) {
        var org_string = Gson().toJson(organisation)
        var route_string = Gson().toJson(route)
        var route_viewz = Gson().toJson(route_view_list(filter_views(route_views)))
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id,ViewRoute.newInstance("","",org_string,route_string,active_route,route_viewz),_view_route).commit()

    }


    protected fun getDisplayPulseRadius(radius: Float): Float {
        val diff: Float = mMap.getMaxZoomLevel() - mMap.getCameraPosition().zoom
        if (diff < 3) return radius
        if (diff < 3.7) return radius * (diff / 2)
        if (diff < 4.5) return radius * diff
        if (diff < 5.5) return radius * diff * 1.5f
        if (diff < 7) return radius * diff * 2f
        if (diff < 7.8) return radius * diff * 3.5f
        if (diff < 8.5) return (radius * diff) * 5
        if (diff < 10) return radius * diff * 10f
        if (diff < 12) return radius * diff * 18f
        if (diff < 13) return radius * diff * 28f
        if (diff < 16) return radius * diff * 40f
        return if (diff < 18) radius * diff * 60 else radius * diff * 80
    }

    private var lastUserCircle: Circle? = null
    private val pulseDuration: Long = 2500
    private var lastPulseAnimator: ValueAnimator? = null
    var rad = 300f
    private fun addPulsatingEffect(userLatlng: LatLng) {
        if (lastPulseAnimator != null) {
            lastPulseAnimator!!.cancel()
            Log.d("onLocationUpdated: ", "cancelled")
        }
        if (lastUserCircle != null) lastUserCircle!!.center = userLatlng
        lastPulseAnimator = valueAnimate( AnimatorUpdateListener { animation ->
                if (lastUserCircle != null) {
//                    Log.e("addPulsatingEffect","animation value is ${(animation.getAnimatedValue() as Float)}")
                    lastUserCircle!!.setRadius((animation.getAnimatedValue()  as Float).toDouble())
                    var col = Color.parseColor("#2271cce7")
                    if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                        col = Color.GRAY
                    }
                    lastUserCircle!!.fillColor = adjustAlpha(col, (rad - (animation.getAnimatedValue() as Float))/rad)
                } else {
//                    Log.e("addPulsatingEffect","animation value is ${(animation.getAnimatedValue() as Float)}")
                    var col = Color.parseColor("#2271cce7")
                    if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                        col = Color.GRAY
                    }
                    lastUserCircle = mMap.addCircle(CircleOptions()
                            .center(userLatlng)
                            .radius((animation.getAnimatedValue() as Float).toDouble())
                            .fillColor(col)
                            .strokeWidth(0f)
                    )
                }
            })
    }

    fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
//        Log.e("adjustAlpha","adjusted alpha is ${alpha}")
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    protected fun valueAnimate( updateListener: AnimatorUpdateListener?): ValueAnimator? {
//        Log.d("valueAnimate: ", "called")
        val va = ValueAnimator.ofFloat(0f, rad)
        va.duration = pulseDuration
        va.addUpdateListener(updateListener)
        va.interpolator = LinearOutSlowInInterpolator()
        va.start()
        return va
    }


    private var lastViewCircle: Circle? = null
    private var lastViewPulseAnimator: ValueAnimator? = null
    var view_rad = 300f
    fun when_route_viewed(view: route_view){
        if(supportFragmentManager.findFragmentByTag(_view_route)!=null){
            (supportFragmentManager.findFragmentByTag(_view_route) as ViewRoute).when_route_views_updated(route_views)
        }

        val userLatlng = view.viewer_lat_lng
        if (lastViewPulseAnimator != null) {
            lastViewPulseAnimator!!.cancel()
            Log.d("onLocationUpdated: ", "cancelled")
        }
        if (lastViewCircle != null) lastViewCircle!!.center = userLatlng
        lastViewPulseAnimator = valueAnimat( AnimatorUpdateListener { animation ->
            if (lastViewCircle != null) {
//                Log.e("addPulsatingEffect","animation value is ${(animation.getAnimatedValue() as Float)}")
                lastViewCircle!!.setRadius((animation.getAnimatedValue()  as Float).toDouble())
                var col = Color.DKGRAY
                if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                    col = Color.LTGRAY
                }
                lastViewCircle!!.fillColor = adjustAlpha(col, (view_rad - (animation.getAnimatedValue() as Float))/view_rad)

                if((animation.getAnimatedValue() as Float)==view_rad){
                    //were done
//                    lastViewCircle!!.remove()
                }
            } else {
//                Log.e("addPulsatingEffect","animation value is ${(animation.getAnimatedValue() as Float)}")
                var col = Color.DKGRAY
                if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                    col = Color.LTGRAY
                }
                lastViewCircle = mMap.addCircle(CircleOptions()
                    .center(userLatlng)
                    .radius((animation.getAnimatedValue() as Float).toDouble())
                    .fillColor(col)
                    .strokeWidth(0f)
                )
            }
        })
    }

    protected fun valueAnimat( updateListener: AnimatorUpdateListener?): ValueAnimator? {
//        Log.d("valueAnimate: ", "called")
        val va = ValueAnimator.ofFloat(0f, view_rad)
        va.duration = pulseDuration
        va.addUpdateListener(updateListener)
        va.interpolator = LinearOutSlowInInterpolator()
        va.start()
        return va
    }

    fun filter_views(views: ArrayList<route_view>): ArrayList<route_view>{
        val new_list: ArrayList<route_view> = ArrayList()
        for(item in views){
            if(Calendar.getInstance().timeInMillis-(1000*60*60)<= item.creation_time){
                new_list.add(item)
            }
        }
        return new_list
    }



    var positions: HashMap<String,ArrayList<driver_pos>> = HashMap()
    var added_postitions: ArrayList<String> = ArrayList()
    var driver_listeners: ArrayList<ListenerRegistration> = ArrayList()

    fun set_up_driver_listeners(){
        remove_driver_liseners()
        set_driver_listener_updates()
    }

    fun set_driver_listener_updates(){
        remove_driver_liseners()
        val t = Calendar.getInstance().timeInMillis
        for(org in organisations){
            val org_listener = db.collection(constants.organisations)
                .document(org.country!!)
                .collection(constants.country_organisations)
                .document(org.org_id!!)
                .collection(constants.driver_locations)
                .whereGreaterThanOrEqualTo("creation_time", t)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w(TAG, "listen:error", e)
                        return@addSnapshotListener
                    }

                    for (dc in snapshots!!.documentChanges) {
                        if(dc.type.equals(DocumentChange.Type.ADDED)){
//                            Log.d(TAG, "New location: ${dc.document.data}")
                        }
                        else if(dc.type.equals(DocumentChange.Type.MODIFIED)){
//                            Log.d(TAG, "Modified location: ${dc.document.data}")
                        }
                        else if(dc.type.equals(DocumentChange.Type.REMOVED)){
//                            Log.d(TAG, "Removed location: ${dc.document.data}")
                        }

                        val pos_id = dc.document["pos_id"] as String
                        val creation_time = dc.document["creation_time"] as Long
                        val user = dc.document["user"] as String
                        val loc = Gson().fromJson(dc.document["loc"] as String, LatLng::class.java)
                        val organisation = dc.document["organisation"] as String
                        val route = dc.document["route"] as String

                        val driverPos = driver_pos(pos_id,creation_time,user,loc,organisation,route)
                        Log.e(TAG, "checking if location item is already there")
                        if(!is_location_contained(driverPos) && Calendar.getInstance().timeInMillis-creation_time <= constants.update_limit){
                            put_position_item(driverPos)
                            added_postitions.add(driverPos.pos_id)
                            Log.e(TAG, "its not! adding")
                            when_driver_position_updated(driverPos)
                        }
                    }
                }
            driver_listeners.add(org_listener)
        }

    }

    fun remove_driver_liseners(){
        if(driver_listeners.isNotEmpty()){
            for(item in driver_listeners){
                item.remove()
            }
            driver_listeners.clear()
        }
    }

    fun when_driver_position_updated(driverPos: driver_pos){
        Log.e(TAG,"Updated driver loc: ${driverPos.pos_id}")
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        if(!driverPos.driver_id.equals(uid))set_drivers_on_map(driverPos.driver_id)
    }

    class driver_pos(var pos_id: String, var creation_time: Long, var driver_id: String, var loc: LatLng, var organisation_id: String, var route_id: String){

    }

    fun is_location_contained(driverPos: driver_pos): Boolean{
//        for(item in positions){
//            if(item.pos_id.equals(driverPos.pos_id)){
//                return true
//            }
//        }
        return added_postitions.contains(driverPos.pos_id)
    }

    fun put_position_item(locationPos: driver_pos){
        if(positions.containsKey(locationPos.driver_id)){
            positions.get(locationPos.driver_id)!!.add(locationPos)
        }else{
            val drivers_locations: ArrayList<driver_pos> = ArrayList()
            drivers_locations.add(locationPos)
            positions.put(locationPos.driver_id,drivers_locations)
        }
    }





    val driver_map_markers: HashMap<String, Marker> = HashMap()
    val driver_map_marker_trail: HashMap<String, ArrayList<Circle>> = HashMap()
    fun set_drivers_on_map(driver: String){
        val drivers_last_locations = get_drivers_last_few_locations(positions.get(driver)!!)
        val drivers_last_location = drivers_last_locations[drivers_last_locations.lastIndex].loc

        if(driver_map_markers.containsKey(driver)){
            driver_map_markers.get(driver)!!.position = drivers_last_location
//            driver_map_markers.remove(driver)
        }else{
            val op = MarkerOptions().position(drivers_last_location)
            val final_icon: BitmapDrawable?  = getDrawable(R.drawable.bus_loc) as BitmapDrawable

            val height = 108
            val width = 55
            if(final_icon!=null) {
                val b: Bitmap = final_icon.bitmap
                val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
                op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
            }

            val driver_marker = mMap.addMarker(op)
            driver_marker.tag = driver
            driver_map_markers.put(driver,driver_marker)
        }

        if(driver_map_marker_trail.containsKey(driver)){
            for(circle in driver_map_marker_trail.get(driver)!!){
                circle.remove()
            }
            driver_map_marker_trail.get(driver)!!.clear()
            driver_map_marker_trail.remove(driver)
        }


        for(last_loc in drivers_last_locations){
            val circleOptions = CircleOptions()
            circleOptions.center(last_loc.loc)
            circleOptions.radius(1.0)
            if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                circleOptions.fillColor(Color.LTGRAY)
            }else{
                circleOptions.fillColor(Color.GREEN)
            }
            circleOptions.strokeWidth(0f)
            val circle = mMap.addCircle(circleOptions)

            if(!driver_map_marker_trail.containsKey(driver)){
                driver_map_marker_trail.put(driver,ArrayList<Circle>())
            }
            driver_map_marker_trail.get(driver)!!.add(circle)
        }
        Handler().postDelayed({
            addPulsatingEffect(drivers_last_locations[drivers_last_locations.lastIndex].loc, driver)
        }, 100)

//        if(viewed_driver.equals(driver))move_camera(drivers_last_locations[drivers_last_locations.lastIndex].loc)
    }

    fun set_all_drivers(){
        for(driver in positions.keys){
            val drivers_last_locations = get_drivers_last_few_locations(positions.get(driver)!!)

            val op = MarkerOptions().position(drivers_last_locations[drivers_last_locations.lastIndex].loc)
            val final_icon: BitmapDrawable?  = getDrawable(R.drawable.bus_loc) as BitmapDrawable

            val height = 108
            val width = 55
            if(final_icon!=null) {
                val b: Bitmap = final_icon.bitmap
                val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
                op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
            }

            if(driver_map_markers.containsKey(driver)){
                driver_map_markers.get(driver)!!.remove()
                driver_map_markers.remove(driver)
            }

            if(driver_map_marker_trail.containsKey(driver)){
                for(circle in driver_map_marker_trail.get(driver)!!){
                    circle.remove()
                }
                driver_map_marker_trail.get(driver)!!.clear()
                driver_map_marker_trail.remove(driver)
            }

            val driver_marker = mMap.addMarker(op)
            driver_map_markers.put(driver,driver_marker)

            for(last_loc in drivers_last_locations){
                val circleOptions = CircleOptions()
                circleOptions.center(last_loc.loc)
                circleOptions.radius(1.0)
                if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    circleOptions.fillColor(Color.LTGRAY)
                }else{
                    circleOptions.fillColor(Color.GREEN)
                }
                circleOptions.strokeWidth(0f)
                val circle = mMap.addCircle(circleOptions)

                if(!driver_map_marker_trail.containsKey(driver)){
                    driver_map_marker_trail.put(driver,ArrayList<Circle>())
                }
                driver_map_marker_trail.get(driver)!!.add(circle)
            }
        }
    }

    fun get_drivers_last_few_locations(drivers_positions: ArrayList<driver_pos>): ArrayList<driver_pos>{
//        val sorted_list: ArrayList<driver_pos> = ArrayList()
//        for(item in drivers_positions.sortedWith(compareBy({ it.creation_time }))){
//            sorted_list.add(item)
//        }

        val last_3_list: ArrayList<driver_pos> = ArrayList()
        last_3_list.addAll(drivers_positions.takeLast(3))

        return last_3_list

    }

    private var lastUserCircleList: HashMap<String,Circle> = HashMap()
    private var lastPulseAnimatorList: HashMap<String,ValueAnimator> = HashMap()
    private fun addPulsatingEffect(userLatlng: LatLng, driver: String) {
        if (lastPulseAnimatorList.containsKey(driver)) {
            lastPulseAnimatorList.get(driver)!!.cancel()
        }
        if (lastUserCircleList.containsKey(driver)) lastUserCircleList.get(driver)!!.center = userLatlng
        var lastPulse = valueAnimate(ValueAnimator.AnimatorUpdateListener { animation ->
            if (lastUserCircleList.containsKey(driver)) {
                lastUserCircleList.get(driver)!!.setRadius((animation.getAnimatedValue() as Float).toDouble())
                var col = Color.parseColor("#2271cce7")
                if (constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    col = Color.GRAY
                }
                lastUserCircleList.get(driver)!!.fillColor = adjustAlpha(col, (rad - (animation.getAnimatedValue() as Float)) / rad)
            } else {
                var col = Color.parseColor("#2271cce7")
                if (constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    col = Color.GRAY
                }
                var lastCircle = mMap.addCircle(CircleOptions()
                    .center(userLatlng)
                    .radius((animation.getAnimatedValue() as Float).toDouble())
                    .fillColor(col)
                    .strokeWidth(0f)
                )
                if(lastUserCircleList.containsKey(driver)){
                    lastUserCircleList.remove(driver)
                }
                lastUserCircleList.put(driver,lastCircle)
            }
        })
        if(lastPulseAnimatorList.containsKey(driver)){
            lastPulseAnimatorList.remove(driver)
        }
        lastPulseAnimatorList.put(driver,lastPulse!!)
    }



}