package com.color.mattdriver.Activities

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.widget.*
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import com.google.firebase.auth.FirebaseAuth
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
    GoogleMap.OnMarkerClickListener
{
    val TAG = "MapsActivity"
    val _welcome = "_welcome"
    val _join_organisation = "_join_organisation"
    val _create_organisation = "_create_organisation"
    val _organisation_passcode =  "_organisation_passcode"
    val _view_organisation = "_view_organisation"
    val _settings = "_settings"

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

    var mapView: View? = null
    val ZOOM = 15f
    val ZOOM_FOCUSED = 16f
    var has_set_my_location = false

    lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    var mLastKnownLocations: ArrayList<Location> = ArrayList()
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
            val orgs = Gson().toJson(organisation.organisation_list(organisations))
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(binding.money.id,JoinOrganisation.newInstance("","", orgs),_join_organisation).commit()
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
    }

    fun open_welcome_fragment(){
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(binding.money.id,Welcome.newInstance("",""),_welcome).commit()
    }

    override fun OnContinueSelected() {
        constants.SharedPreferenceManager(applicationContext).setFirstTimeLaunch(false)
        onBackPressed()

    }

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

            } else super.onBackPressed()
        }
    }

    fun removing_fragment_notifier(tag: String){
        if(tag.equals(_view_organisation)){
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
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
            locationRequest.setInterval(10 * 1000)


            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (locationResult == null) {
                        return
                    }
                    for (location in locationResult.locations) {
                        if (location != null) {
                            wayLatitude = location.latitude
                            wayLongitude = location.longitude
                            Log.e(TAG,"wayLatitude: ${wayLatitude} longitude: ${wayLongitude}")
                            if(!has_set_my_location){
                                has_set_my_location = true
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude,
                                    location.longitude), ZOOM))
                            }
                            mLastKnownLocations.add(location)
                            when_location_gotten()
                        }
                    }
                }
            }
            mFusedLocationClient.requestLocationUpdates(locationRequest,locationCallback,null)

        }
    }

    override fun onDestroy() {
        Log.e(TAG,"onDestroy")
        super.onDestroy()
        if (this.mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(locationCallback)
        }
        store_session_data()
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG,"onStop")

    }

    override fun onStart() {
        Log.e(TAG,"onStart")
        super.onStart()
        set_session_data()
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

                        organisations.add(org)
                    }
                }
                store_session_data()
                if(supportFragmentManager.findFragmentByTag(_join_organisation)!=null){
                    (supportFragmentManager.findFragmentByTag(_join_organisation) as JoinOrganisation)
                        .onOrganisationListReloaded(organisations)
                }
            }
    }

    fun load_routes(){
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
        routes.clear()
        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_organisations)
            .get().addOnSuccessListener {
                if(it.documents.isNotEmpty()){
                    for(item in it.documents) {
                        val organisation_id = item["organisation_id"] as String
                        val creation_time = item["creation_time"] as String
                        val route_id = item["route_id"] as String
                        val country = item["country"] as String
                        val creater = item["creater"] as String

                        val route = Gson().fromJson(item["route"].toString(), route::class.java)

                        routes.add(route)
                    }
                }
                store_session_data()
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
            var org_string = Gson().toJson(organisation)
            var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(organisation)))
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string),_view_organisation).commit()

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

        val data = hashMapOf(
            "name" to name,
            "org_id" to org_ref.id,
            "country" to country_name,
            "creater" to uid,
            "creation_time" to time
        )

        val new_org = organisation(name,time)
        new_org.org_id = org_ref.id
        new_org.country = country_name

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
                    route_string),_view_organisation).commit()
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
                        my_organisations.add(organisation.org_id!!)
                        var org_string = Gson().toJson(organisation)
                        var route_string = Gson().toJson(route.route_list(load_my_organisations_routes(organisation)))
                        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                            .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string),_view_organisation).commit()

                        if(!!constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!.email
                                .equals(constants.unknown_email)) {
                            //the user is a registered user.
                            val uid = FirebaseAuth.getInstance().currentUser!!.uid
                            db.collection(constants.coll_users).document(uid)
                                .collection(constants.my_organisations)
                                .document(organisation.org_id!!)
                                .set(hashMapOf(
                                        "name" to organisation.name,
                                        "org_id" to organisation.org_id,
                                        "creation_time" to organisation.creation_time
                                    ))
                        }
                        active_organisation = organisation.org_id!!
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

    override fun viewRoute(route: route, organisation: organisation) {
        var org_string = Gson().toJson(organisation)
        var route_string = Gson().toJson(route)
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .add(binding.money.id,ViewOrganisation.newInstance("","",org_string,route_string),_view_organisation).commit()


//        viewing_route = route
//        load_my_route(route)
//        openRouteCreater(organisation)
    }


    fun store_session_data(){
        val session = Gson().toJson(session_data(organisations,active_organisation, my_organisations, routes))
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
        }
    }

    class session_data(var organisations: ArrayList<organisation>,var active_organisation: String,
                       var my_organisations: ArrayList<String>,var routes: ArrayList<route>): Serializable




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
            val fields: List<Place.Field> = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG)
            val intent: Intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        }
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
        hide_normal_home_items()
        is_creating_routes = true
        load_bus_stop_adapter()

        binding.setStartingLayout.setOnClickListener {
            if(is_picking_destination_location) onBackPressed()
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
            if(is_picking_source_location) onBackPressed()
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
        }

        if(set_end_pos!=null){
            binding.stopsLocationPart.visibility = View.VISIBLE
        }

        if(set_end_pos!=null && set_start_pos!=null){
            binding.finishCreateRoute.visibility = View.VISIBLE
        }
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

        val height = 90
        val width = 35
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
                    v.delete_icon.visibility = View.VISIBLE
                    selected_stop = stop.creation_time.toString()
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
    }

    fun show_normal_home_items(){
        binding.settings.visibility = View.VISIBLE
        binding.bottomSheetLayout.visibility = View.VISIBLE
        binding.money.visibility = View.VISIBLE
        binding.newRouteView.visibility = View.GONE
    }

    override fun onMyLocationClick(p0: Location) {

    }

    fun when_location_gotten(){
        val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)

//        val circleOptions = CircleOptions()
//        circleOptions.center(LatLng(last_loc.latitude,last_loc.longitude))
//        circleOptions.radius(1.0)
//        circleOptions.strokeColor(R.color.colorPrimary)
//        circleOptions.fillColor(R.color.colorAccent)
//        circleOptions.strokeWidth(1f)
//        val circle = mMap.addCircle(circleOptions)
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
                    Log.e(TAG, "leg start adress: ${leg.start_address}")
                    for(step in leg.steps){
                        Log.e(TAG,"step maneuver: ${step.maneuver}")
                        val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                        entire_path.add(pathh)
                    }
                }
            }
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
            val op = PolylineOptions()
                .addAll(entire_paths[i])
                .width(3f)
                .color(applicationContext.getResources().getColor(R.color.route_color))
            drawn_polyline.add(mMap.addPolyline(op))
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
            .document(organisation.org_id!!)
            .collection(constants.routes)
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

    fun update_route(route: route, organisation: organisation){
        showLoadingScreen()

        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val route_ref = db.collection(constants.organisations)
            .document(organisation.country!!)
            .collection(constants.country_routes)
            .document(organisation.org_id!!)
            .collection(constants.routes)
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
            "creater" to uid
        )

        route_ref.update(data).addOnSuccessListener {
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

    fun remove_route(route: route, organisation: organisation){
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
                Log.e(TAG, "leg start adress: ${leg.start_address}")
                for(step in leg.steps){
                    Log.e(TAG,"step maneuver: ${step.maneuver}")
                    val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                    entire_path.add(pathh)
                }
            }
        }

        draw_route(entire_path)
        add_marker(set_start_pos!!,constants.start_loc, constants.start_loc)
        add_marker(set_end_pos!!,constants.end_loc, constants.end_loc)
        for(item in added_bus_stops){
            add_marker(item.stop_location,constants.stop_loc, constants.stop_loc)
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

}