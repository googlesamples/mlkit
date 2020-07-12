package com.google.mlkit;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;


public abstract class NavigationActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_material_showcase) {
            openActivity(com.google.mlkit.md.MainActivity.class);
        } else if (id == R.id.nav_vision_quickstart) {
            openActivity(com.google.mlkit.vision.demo.EntryChoiceActivity.class);
        } else if (id == R.id.nav_translate_showcase) {
            openActivity(com.google.mlkit.showcase.translate.MainActivity.class);
        } else if (id == R.id.nav_translate) {
            openActivity(com.google.mlkit.samples.translate.EntryChoiceActivity.class);
        } else if (id == R.id.nav_smart_replay) {
            openActivity(com.google.mlkit.samples.smartreply.EntryChoiceActivity.class);
        } else if (id == R.id.nav_langid) {
            openActivity(com.google.mlkit.samples.languageid.EntryChoiceActivity.class);
        } else if (id == R.id.nav_auto_ml) {
            openActivity(com.google.mlkit.vision.automl.demo.ChooserActivity.class);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected void openActivity(Class clazz) {
        startActivity(new Intent(this, clazz));
        if (clazz.isInstance(NavigationActivity.class)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        supportInvalidateOptionsMenu();
    }

}

