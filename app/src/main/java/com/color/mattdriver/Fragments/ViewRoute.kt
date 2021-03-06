package com.color.mattdriver.Fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Switch
import android.widget.TextView
import com.color.mattdriver.Activities.MapsActivity
import com.color.mattdriver.Constants
import com.color.mattdriver.Models.organisation
import com.color.mattdriver.Models.route
import com.color.mattdriver.R
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson


class ViewRoute : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private val ARG_PARAM1 = "param1"
    private val ARG_PARAM2 = "param2"
    private val ARG_ORG = "ARG_ORG"
    private val ARG_ROUTE = "ARG_ROUTE"
    private val ARG_ROUTE_VIEWS = "ARG_ROUTE_VIEWS"
    private val ARG_PICKED_ROUTE = "ARG_PICKED_ROUTE"
    private lateinit var my_organisation: organisation
    private lateinit var my_route: route
    private lateinit var listener: ViewRouteInterface
    private var set_route : String = ""
    var route_views: ArrayList<MapsActivity.route_view> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
            my_organisation = Gson().fromJson(it.getString(ARG_ORG), organisation::class.java)
            my_route = Gson().fromJson(it.getString(ARG_ROUTE), route::class.java)
            set_route = it.getString(ARG_PICKED_ROUTE)!!
            route_views = Gson().fromJson(it.getString(ARG_ROUTE_VIEWS), MapsActivity.route_view_list::class.java).route_views
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is ViewRouteInterface){
            listener = context
        }
    }



    var when_route_picked: (route: String) -> Unit = {}

    var when_route_disabled: (is_disabled: Boolean) -> Unit = {}

    var when_route_views_updated: (new_route_views: ArrayList<MapsActivity.route_view>) -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val va = inflater.inflate(R.layout.fragment_view_route, container, false)
        val set_route_layout: RelativeLayout = va.findViewById(R.id.set_route_layout)
        val source_text: TextView = va.findViewById(R.id.source_text)
        val destination_text: TextView = va.findViewById(R.id.destination_text)
        val stops_text: TextView = va.findViewById(R.id.stops_text)
        val organisation_name: TextView = va.findViewById(R.id.organisation_name)
        val edit_route_layout: RelativeLayout = va.findViewById(R.id.edit_route_layout)
        val edit_layout: RelativeLayout = va.findViewById(R.id.edit_layout)
        val remove_route_switch: Switch = va.findViewById(R.id.remove_route_switch)
        val disable_layout: RelativeLayout = va.findViewById(R.id.disable_layout)
        val number_of_views_layout: LinearLayout = va.findViewById(R.id.number_of_views_layout)
        val views_text: TextView = va.findViewById(R.id.views_text)

        val money: RelativeLayout = va.findViewById(R.id.money)

        money.setOnTouchListener { v, event -> true }


        organisation_name.text = "${my_organisation.name}, ${my_organisation.country}"
        stops_text.text = "${my_route.added_bus_stops.size} stops."
        destination_text.text = my_route.ending_pos_desc
        source_text.text = my_route.starting_pos_desc

        set_route_layout.setOnClickListener {
            Constants().touch_vibrate(context)
            listener.whenSetRoute(my_route,my_organisation)
        }

        when_route_picked = {
            set_route = it
            if(set_route.equals(my_route.route_id)){
                set_route_layout.visibility = View.GONE
            }else{
                set_route_layout.visibility = View.VISIBLE
            }
        }

        when_route_picked(set_route)

        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        if((my_organisation.admins!=null && my_organisation.admins.admins.contains(uid)) || uid.equals(Constants().pass)) {
            edit_layout.visibility = View.VISIBLE
            disable_layout.visibility = View.VISIBLE
        }

        edit_route_layout.setOnClickListener {
            Constants().touch_vibrate(context)
            listener.whenEditRoute(my_route,my_organisation)
        }

        remove_route_switch.isChecked = my_route.disabled

        remove_route_switch.setOnCheckedChangeListener { compoundButton, b ->
            Constants().touch_vibrate(context)
            listener.whenDisableRoute(b,my_route,my_organisation)
        }

        when_route_disabled = {
            my_route.disabled = it
        }

        if(route_views.isNotEmpty()){
            number_of_views_layout.visibility = View.VISIBLE
            if(route_views.size==1){
                views_text.text = "1 view."
            }else{
                views_text.text = "${route_views.size} views."
            }
        }

        when_route_views_updated = {
            route_views = it
            if(route_views.isNotEmpty()){
                number_of_views_layout.visibility = View.VISIBLE
                if(route_views.size==1){
                    views_text.text = "1 view."
                }else{
                    views_text.text = "${route_views.size} views."
                }
            }
        }

        return va
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String,org: String, route: String, picked_route: String, route_views: String) =
            ViewRoute().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                    putString(ARG_ORG,org)
                    putString(ARG_ROUTE,route)
                    putString(ARG_PICKED_ROUTE,picked_route)
                    putString(ARG_ROUTE_VIEWS,route_views)
                }
            }
    }

    interface ViewRouteInterface{
        fun whenSetRoute(route: route,organisation: organisation)
        fun whenEditRoute(route: route,organisation: organisation)
        fun whenDisableRoute(is_disabled: Boolean, route: route,organisation: organisation)
    }
}