package castofo_nower.com.co.nower.controllers;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import castofo_nower.com.co.nower.R;
import castofo_nower.com.co.nower.connection.HttpHandler;
import castofo_nower.com.co.nower.helpers.AlertDialogsResponse;
import castofo_nower.com.co.nower.helpers.GeolocationInterface;
import castofo_nower.com.co.nower.helpers.ParsedErrors;
import castofo_nower.com.co.nower.helpers.SubscribedActivities;
import castofo_nower.com.co.nower.models.Branch;
import castofo_nower.com.co.nower.models.User;
import castofo_nower.com.co.nower.support.ImageDownloader;
import castofo_nower.com.co.nower.support.RequestErrorsHandler;
import castofo_nower.com.co.nower.support.UserFeedback;
import castofo_nower.com.co.nower.support.Geolocation;
import castofo_nower.com.co.nower.models.MapData;
import castofo_nower.com.co.nower.models.Promo;


public class NowerMap extends FragmentActivity implements SubscribedActivities,
GeolocationInterface, AlertDialogsResponse, ParsedErrors,
GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowClickListener {

  private GoogleMap map;
  private float ZOOM_LEVEL = 13.1f;
  private int TILT_LEVEL = 60;
  private int RANGE_IN_METERS = 3100;
  private Geolocation geolocation;
  public Marker userMarker = null;
  public Marker currentMarker = null;
  public Circle userRange;

  private HttpHandler httpHandler = new HttpHandler();
  public static final String ACTION_PROMOS = "/promos/locations";
  private Map<String, String> params = new HashMap<String, String>();

  private ProgressDialog progressDialog = null;

  private UserFeedback userFeedback = new UserFeedback();

  private RequestErrorsHandler requestErrorsHandler = new
                                                      RequestErrorsHandler();

  // Para la gestión de los marcadores no es posible utilizar TreeMap, ya que
  // los marcadores no son comparables.
  private Map<Marker, Integer> branchesIdsMap = new HashMap<>();
  private Map<Integer, Branch> branchesMap = new TreeMap<>();
  private Map<Integer, Promo> promosMap = new TreeMap<>();

  public static final double NO_USER_LAT = -1.0;
  public static final double NO_USER_LONG = -1.0;

  public static final int OP_SUCCEEDED = 0;
  public static final String SHOW_BRANCH_PROMOS = "SHOW_BRANCH_PROMOS";

  public static final String NO_MAP = "NO_MAP";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_nower_map);

    geolocation = new Geolocation(NowerMap.this);
    // Se indica a Geolocation la actividad que estará esperando el aviso de
    // cambio de localización.
    geolocation.addListeningActivity(this);
    // Se indica al HttpHandler la actividad que estará esperando la respuesta
    // a la petición.
    httpHandler.addListeningActivity(this);

    requestErrorsHandler.addListeningActivity(this);

    userFeedback.addListeningActivity(this);

    // Se captura el mapa dentro de la variable map para poderlo gestionar.
    map = ((SupportMapFragment) getSupportFragmentManager()
           .findFragmentById(R.id.map)).getMap();

    if (map != null) {
      getActionBar().setDisplayHomeAsUpEnabled(true);
      setUpMap();
      setMapListeners();
      // Ya estaba previamente capturada la localización del usuario.
      if (MapData.userLat != NO_USER_LAT && MapData.userLong != NO_USER_LONG) {
        moveCameraToPosition(MapData.userLat, MapData.userLong);
      }
      verifyLocationProviders();
    }
    else {
      UserFeedback
      .showAlertDialog(this, R.string.sorry,
                       getResources().getString(R.string.error_loading_map),
                       R.string.exit, UserFeedback.NO_BUTTON_TO_SHOW, NO_MAP);
    }
  }

  public void setUpMap() {
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    // Se remueven los controles de zoom para que solamente funcione con
    // pinch-zoom.
    map.getUiSettings().setZoomControlsEnabled(false);
    // Se muestra la brújula dentro del mapa para indicar el Norte.
    map.getUiSettings().setCompassEnabled(true);
    // Se activa la geolocalización del usuario.
    map.setMyLocationEnabled(true);
  }

  public void setMapListeners() {
    map.setOnMarkerClickListener(this);
    map.setOnInfoWindowClickListener(this);
  }

  public void verifyLocationProviders() {
    geolocation.verifyLocationPossibilities();
    if (geolocation.canGetLocation()) {
      setLocProgressDialog();
      geolocation.getUserLocation();
    }
    else {
      // Si no es posible obtener la localización, se muestra un diálogo para
      // activar el GPS.
      geolocation.askToEnableGPS();
    }
  }

  public void setLocProgressDialog() {
    if (MapData.userLat == NO_USER_LAT && MapData.userLong == NO_USER_LONG) {
      // Se muestra un mensaje de progreso al usuario si aún no se tenía una
      // localización previa.
      progressDialog = new ProgressDialog(this);
      progressDialog.setMessage(getResources()
                                .getString(R.string.obtaining_your_location));
      progressDialog.setCanceledOnTouchOutside(false);
      progressDialog.show();
    }
  }

  // Este método se utiliza para animar los cambios de ubicación del usuario.
  public void animateCameraToPosition(final double lat, final double lon) {
    CameraPosition cameraPosition = setCameraPosition(lat, lon);
    map.animateCamera(CameraUpdateFactory
                      .newCameraPosition(cameraPosition),
                      new GoogleMap.CancelableCallback() {
                        @Override
                        public void onFinish() {
                          putUserMarker(lat, lon);
                          // Con el mapa centrado en la localización del usuario
                          // es tiempo de mostrar las promociones.
                          setUserLocationParams();
                          sendRequest(ACTION_PROMOS);
                        }

                        @Override
                        public void onCancel() { }
                      });
  }

  // En caso de que recientemene se haya actualizado la localización del
  // usuario, el mapa se centra inmediatamente allí.
  public void moveCameraToPosition(double lat, double lon) {
    CameraPosition cameraPosition = setCameraPosition(lat, lon);
    map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    putUserMarker(lat, lon);
    setUserLocationParams();
    sendRequest(ACTION_PROMOS);
  }

  // Se inclina el mapa para mejorar la visión.
  public CameraPosition setCameraPosition(double latitude, double longitude) {
    CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(new LatLng(latitude, longitude))
                                    .tilt(TILT_LEVEL)
                                    .zoom(ZOOM_LEVEL)
                                    .bearing(0).build();
    return cameraPosition;
  }

  // Se pone un marcador para indicarle al usuario su posición actual.
  public void putUserMarker(double latitude, double longitude) {
    // Es la primera vez que se va a poner el marcador del usuario.
    if (userMarker == null) {
      userMarker = map.addMarker(new MarkerOptions()
                                 .position(new LatLng(latitude, longitude))
                                 .title(getResources()
                                        .getString(R.string.you_are_here))
                                 .icon(BitmapDescriptorFactory
                                       .fromResource(R.drawable.user_marker)));
      userRange = map.addCircle(new CircleOptions()
              .center(new LatLng(latitude, longitude))
              .radius(RANGE_IN_METERS)
              .strokeWidth(1f)
              .strokeColor(Color.BLUE)
              .fillColor(getResources().getColor
                      (R.color.transparent_blue)));
    }
    // El marcador ya existía pero se debe mover, ya que la localización del
    // usuario cambió.
    else {
      userMarker.setPosition(new LatLng(latitude, longitude));
      userRange.setCenter(new LatLng(latitude, longitude));
    }
    userMarker.showInfoWindow();
  }

  // Este método construye un mapa con los datos de la localización del usuario,
  // con el fin de mostrar únicamente las promociones cercanas a su ubicación.
  public void setUserLocationParams() {
    params.put("latitude", String.valueOf(MapData.userLat));
    params.put("longitude", String.valueOf(MapData.userLong));
  }

  public void sendRequest(String request) {
    if (request.equals(ACTION_PROMOS)) {
      httpHandler.sendRequest(HttpHandler.NAME_SPACE, ACTION_PROMOS, "", params,
                              new HttpPost(),NowerMap.this);
    }
    else if (SplashActivity.isThereLoginInstance()) {
      if (request.equals(UserPromosList.ACTION_USER_REDEMPTIONS)) {
        httpHandler.sendRequest(HttpHandler.NAME_SPACE,
                                UserPromosList.ACTION_USER_REDEMPTIONS, "/"
                                + User.id, params, new HttpGet(),
                                NowerMap.this);
      }
    }
  }

  // Este método se encarga de poner todas las promociones en el mapa y
  // guardarlas los establecimientos localmente.
  public void putMarkerAndSaveBranch(Branch branch) {
    View bubbleMarker =
            ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
            .inflate(R.layout.bubble_marker, null);

    Bitmap markerIcon =
            ImageDownloader.createBitmapFromView(this, bubbleMarker);

    // Se muestra un marker (burbuja) sin logo inicialmente, hasta cargar
    // la imagen.
    Marker branchMarker = map.addMarker(new MarkerOptions()
            .position(new LatLng(branch.getLatitude(), branch.getLongitude()))
            .title(branch.getStoreName() + " - " + branch.getName())
            .icon(BitmapDescriptorFactory.fromBitmap(markerIcon)));


    // Se asocia cada establecimiento a un marcador diferente.
    branchesIdsMap.put(branchMarker, branch.getId());

    // Se solicita obtener la imagen del establecimiento, una vez se obtenga,
    // la clase ImageDownloader se encargará de reemplazar el ícono del marker
    if (branch.getStoreLogoURL() != null) {
      ImageDownloader imageDownloader
      = new ImageDownloader(bubbleMarker, branchMarker, this,
                            branch.getStoreLogoURL());
      imageDownloader.execute();
    }
    else {
      ImageView logoView =
              (ImageView) bubbleMarker.findViewById(R.id.marker_logo);
      logoView.setImageResource(R.drawable.nower_marker);
      markerIcon = ImageDownloader.createBitmapFromView(this, bubbleMarker);
      branchMarker.setIcon(BitmapDescriptorFactory.fromBitmap(markerIcon));
    }
  }

  public void clearPreviousMarkers() {
    for (Map.Entry<Marker, Integer> markerBranchId
         : MapData.getBranchesIdsMap().entrySet()) {
      markerBranchId.getKey().remove();
    }
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (requestCode == Geolocation.ENABLE_GPS_CODE) {
      if (resultCode == OP_SUCCEEDED) {
        //El usuario activó el GPS.
        geolocation.verifyLocationPossibilities();
        if (geolocation.canGetLocation()) {
          setLocProgressDialog();
          // Este método se invoca con el fin de que el listener de la
          // localización sea encendido, ya que se activó el GPS.
          geolocation.getUserLocation();
        }
        // Ahora se espera el cambio de localización para centrar el mapa.
        // Este cambio se recibe a través de la interface GeolocationInterface.
      }
    }
  }

  @Override
  public void notifyLocationChange(double latitude, double longitude) {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
    // Se actualizan con la última localización del usuario obtenida.
    MapData.userLat = latitude;
    MapData.userLong = longitude;
    animateCameraToPosition(latitude, longitude);
  }

  @Override
  public void notifyUserResponse(String action, int buttonPressedId) {
    switch (action) {
      case NO_MAP:
        if (buttonPressedId == R.string.exit) {
          SplashActivity.handleRequest(this, NO_MAP);
        }
        break;
    }
  }

  @Override
  public void notifyParsedErrors(String action,
                                 Map<String, String> errorsMessages) {
    switch (action) {
      case UserPromosList.ACTION_USER_REDEMPTIONS:
        if (errorsMessages.containsKey("user")) {
          UserFeedback.showToastMessage(getApplicationContext(),
                                        errorsMessages.get("user"),
                                        Toast.LENGTH_LONG);
        }
        //TODO cerrar sesión porque se intentó utilizar un usuario inválido.
        break;
    }
  }

  @Override
  public void notify(String action, JSONObject responseJson) {
    try {
      Log.i("responseJson", responseJson.toString());
      int responseStatusCode = responseJson.getInt(HttpHandler.HTTP_STATUS);
      if (action.equals(ACTION_PROMOS)) {
        switch (responseStatusCode) {
          case HttpHandler.OK:
            branchesIdsMap.clear();
            branchesMap.clear();
            promosMap.clear();

            JSONArray locations = responseJson.getJSONArray("locations");
            // Se recorren todas las promociones obtenidas para dibujarlas en
            // el mapa.
            for (int i = 0; i < locations.length(); ++i) {
              ArrayList<Integer> promoList = new ArrayList<>();

              JSONObject internLocation = locations.getJSONObject(i);
              int id = internLocation.getInt("id");
              String name = internLocation.getString("name");
              double latitude = internLocation.getDouble("latitude");
              double longitude = internLocation.getDouble("longitude");
              int storeId = internLocation.getInt("store_id");
              String storeName = internLocation.getString("store_name");
              String storeLogoURL;
              if (internLocation.isNull("store_logo")) storeLogoURL = null;
              else storeLogoURL = internLocation.getString("store_logo");

              JSONArray promos = internLocation.getJSONArray("promos");
              for (int j = 0; j < promos.length(); ++j) {
                JSONObject internPromo = promos.getJSONObject(j);
                int promoId = internPromo.getInt("id");
                String title = internPromo.getString("title");
                String expirationDate = internPromo
                                        .getString("expiration_date");
                int availableRedemptions = internPromo
                                           .getInt("available_redemptions");
                // Se genera la lista de promociones para esa localización,
                // aún sin descripción ni términos.
                Promo promo = new Promo(promoId, title, expirationDate,
                                        availableRedemptions, null, null);
                promoList.add(promo.getId());

                // Se agrega la promoción a un mapa de promociones.
                promosMap.put(promoId, promo);
              }

              Branch branch = new Branch(id, name, latitude, longitude, storeId,
                                         storeName, storeLogoURL, promoList);
              branchesMap.put(branch.getId(), branch);

              putMarkerAndSaveBranch(branch);
            }

            // Se borran los marcadores previos que indicaban la ubicación de
            // los estabecimientos.
            clearPreviousMarkers();

            // Se envían los mapas construidos al modelo MapData para que
            // puedan ser accedidos desde otras actividades.
            // Se debe borrar explícitamente este branchesIdsMap debido a que
            // cada marcador agregado tiene un ID diferente. De no hacer esto,
            // los marcadores no se soobreescribirían sino que se agregarían
            // infinitamente.
            MapData.clearBranchesIdsMap();
            MapData.setBranchesIdsMap(branchesIdsMap);

            MapData.clearBranchesMap();
            MapData.setBranchesMap(branchesMap);

            MapData.clearPromosMap();
            MapData.setPromosMap(promosMap);

            // Se hace para actualizar las promociones que el usuario ha
            // obtenido.
            sendRequest(UserPromosList.ACTION_USER_REDEMPTIONS);
            break;
        }
      }
      else if (action.equals(UserPromosList.ACTION_USER_REDEMPTIONS)) {
        switch (responseStatusCode) {
          case HttpHandler.OK:
            UserPromosList
            .updateUserRedemptions(responseJson.getJSONArray("redemptions"));
            break;
          case HttpHandler.UNAUTHORIZED:
            RequestErrorsHandler
            .parseErrors(action, responseJson.getJSONObject("errors"));
            break;
        }
      }

      params.clear();

    }
    catch (JSONException e) {

    }
  }

  @Override
  public boolean onMarkerClick(Marker marker) {
    if (!marker.equals(userMarker)) {
      if (currentMarker == null) {
        marker.showInfoWindow();
        currentMarker = marker;
      }
      else {
        if (currentMarker.equals(marker)) {
          marker.hideInfoWindow();
          currentMarker = null;
        }
        else {
          currentMarker.hideInfoWindow();
          marker.showInfoWindow();
          currentMarker = marker;
        }
      }

      return true;
    }

    return false;
  }

  @Override
  public void onInfoWindowClick(Marker marker) {
    // Se activa el detector de gestos para animar las tarjetas de promociones.
    if (!marker.equals(userMarker)) {
      int branchId = MapData.getBranchesIdsMap().get(marker);
      Intent showPromos = new Intent(NowerMap.this, PromoCardsAnimator.class);
      showPromos.putExtra("action", SHOW_BRANCH_PROMOS);
      showPromos.putExtra("branch_id", branchId);
      showPromos.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      startActivity(showPromos);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_nower_map, menu);
    if (!SplashActivity.isThereLoginInstance()) {
      menu.findItem(R.id.action_log_out).setVisible(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    switch (id) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.action_log_out:
        SplashActivity.handleRequest(NowerMap.this, UserPromosList.LOG_OUT);
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

}
