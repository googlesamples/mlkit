package com.google.mlkit

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.mlkit.md.MainActivity
import com.google.mlkit.samples.vision.digitalink.DigitalInkMainActivity
import com.google.mlkit.vision.automl.demo.ChooserActivity
import com.google.mlkit.vision.demo.EntryChoiceActivity

abstract class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val headerLayout = navigationView.getHeaderView(0)
        val textVersion = headerLayout.findViewById<TextView>(R.id.textVersion)
        textVersion.text = BuildConfig.VERSION
    }

    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val id = item.itemId
        if (id == R.id.nav_material_showcase) {
            openActivity(MainActivity::class.java)
        } else if (id == R.id.nav_vision_quickstart) {
            openActivity(EntryChoiceActivity::class.java)
        } else if (id == R.id.nav_translate_showcase) {
            openActivity(com.google.mlkit.showcase.translate.MainActivity::class.java)
        } else if (id == R.id.nav_translate) {
            openActivity(com.google.mlkit.samples.translate.EntryChoiceActivity::class.java)
        } else if (id == R.id.nav_smart_replay) {
            openActivity(com.google.mlkit.samples.smartreply.EntryChoiceActivity::class.java)
        } else if (id == R.id.nav_entityextraction) {
            openActivity(com.google.mlkit.samples.nl.entityextraction.kotlin.MainActivityKotlin::class.java)
        } else if (id == R.id.nav_langid) {
            openActivity(com.google.mlkit.samples.languageid.EntryChoiceActivity::class.java)
        } else if (id == R.id.nav_auto_ml) {
            openActivity(ChooserActivity::class.java)
        } else if (id == R.id.nav_digital_ink) {
            openActivity(DigitalInkMainActivity::class.java)
        }
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    protected fun openActivity(clazz: Class<*>) {
        startActivity(Intent(this, clazz))
        if (clazz.isInstance(NavigationActivity::class.java)) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        supportInvalidateOptionsMenu()
    }
}