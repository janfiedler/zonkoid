package eu.urbancoders.zonkysniper.integration;

import android.util.Log;
import com.google.gson.Gson;
import eu.urbancoders.zonkysniper.dataobjects.Investment;
import eu.urbancoders.zonkysniper.events.BetatesterCheck;
import eu.urbancoders.zonkysniper.events.BetatesterRegister;
import eu.urbancoders.zonkysniper.events.Bugreport;
import eu.urbancoders.zonkysniper.events.FcmTokenRegistration;
import eu.urbancoders.zonkysniper.events.GetInvestmentsByZonkoid;
import eu.urbancoders.zonkysniper.events.LogInvestment;
import eu.urbancoders.zonkysniper.events.RegisterThirdpartyNotif;
import eu.urbancoders.zonkysniper.events.UnregisterThirdpartyNotif;
import okhttp3.OkHttpClient;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.List;

/**
 * Author: Ondrej Steger (ondrej@steger.cz)
 * Date: 04.07.2016
 */
public class UrbancodersClient {

    private static final String TAG = UrbancodersClient.class.getName();
    private static final String BASE_URL = "http://urbancoders.eu/";
//    private static final String BASE_URL = "http://10.0.2.2:8080/";  // TOxDO remove fejk URL

    private static Retrofit retrofit;
    private UrbancodersService ucService;

    public UrbancodersClient() {

        EventBus.getDefault().register(this);

        ZonkoidLoggingInterceptor interceptor = new ZonkoidLoggingInterceptor();
        interceptor.setLevel(ZonkoidLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .client(client)
                .build();

        ucService = retrofit.create(UrbancodersService.class);
    }

    @Subscribe
    public void isBetatester(BetatesterCheck.Request evt) {

        if(evt.getUsername() != null) {

            Call<String> call = ucService.isBetatester(evt.getUsername());

            call.enqueue(new Callback<String>() {
                @Override
                public void onResponse(Call<String> call, Response<String> response) {
                    boolean isBetatester = Boolean.getBoolean(response.body());
                    EventBus.getDefault().post(new BetatesterCheck.Response(isBetatester));
                }

                @Override
                public void onFailure(Call<String> call, Throwable t) {
                    // nedelej nic, proste neni to betatester
                }
            });
        }
    }

    @Subscribe
    @Deprecated
    public void requestBetaRegistration(final BetatesterRegister.Request evt) {
        if (evt.getUsername() != null && !evt.getUsername().equalsIgnoreCase("nekdo@zonky.cz")) {
            Call<String> call = ucService.requestBetaRegistration(evt.getUsername());

            call.enqueue(new Callback<String>() {
                @Override
                public void onResponse(Call<String> call, Response<String> response) {
                    Log.i(TAG, "Zadost o beta registraci odeslana na jmeno "+evt.getUsername());
                }

                @Override
                public void onFailure(Call<String> call, Throwable t) {
                    Log.e(TAG, "Selhalo odeslani zadosti o beta registraci na jmeno " + evt.getUsername());
                }
            });
        }
    }

    @Subscribe
    public void sendBugreport(Bugreport.Request evt) {
        Call<String> call = ucService.sendBugreport(
                evt.getUsername(),
                evt.getDescription(),
                evt.getLogs(),
                evt.getTimestamp());

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                Log.i(TAG, "Ulozeni bugreportu probehlo v poradku");
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.e(TAG, "Nezdarilo se ulozeni bugreportu. "+t.getMessage());
            }
        });
    }

    /**
     * Zaloguje investici, asynchronne tak, aby neobtezoval thready
     * @param evt
     */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void logInvestment(LogInvestment.Request evt) {
        Call<Void> call = ucService.logInvestment(
                evt.getUsername(),
                evt.getMyInvestment()
        );

        try {
            call.execute();
        } catch (IOException e) {
            Log.w(TAG, "Failed to log investment to zonkycommander. "+e.getMessage());
        }
    }

    /**
     * Ziskat seznam investic pres Zonkoida do dane pujcky
     * @param evt
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void getInvestmentsByZonkoid(GetInvestmentsByZonkoid.Request evt) {
        Call<List<Investment>> call = ucService.getInvestmentsByZonkoid(
                evt.getLoanId()
        );

        try {
            Response<List<Investment>> response = call.execute();
            if(response != null && response.isSuccessful()) {
                EventBus.getDefault().post(new GetInvestmentsByZonkoid.Response(response.body()));
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to get investments by Zonkoid. "+e.getMessage());
        }

    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void registerUserAndThirdParty(RegisterThirdpartyNotif.Request evt) {
        Call<String> call = ucService.registerUserAndThirdParty(evt.getUsername(), evt.getClientApp().name());

        try {
            Response<String> response = call.execute();
            if (response != null && response.isSuccessful()) {
                EventBus.getDefault().post(new RegisterThirdpartyNotif.Response(Integer.parseInt(response.body())));
            } else {
                EventBus.getDefault().post(new RegisterThirdpartyNotif.Failure());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to registerUserAndThirdParty and get code", e);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void unregisterUserAndThirdParty(UnregisterThirdpartyNotif.Request evt) {
        Call<String> call = ucService.unregisterUserAndThirdParty(evt.getUsername(), evt.getClientApp().name());

        try {
            Response<String> response = call.execute();
            if (response != null && response.isSuccessful()) {
                EventBus.getDefault().post(new UnregisterThirdpartyNotif.Response());
            } else {
                EventBus.getDefault().post(new RegisterThirdpartyNotif.Failure());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregisterUserAndThirdParty and get code", e);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void registerUserToFcm(FcmTokenRegistration.Request evt) {
        Call<Void> call = ucService.registerUserToFcm(evt.getUsername(), evt.getToken());

        try {
            Response<Void> response = call.execute();
            if (response != null && response.isSuccessful()) {
                EventBus.getDefault().post(new FcmTokenRegistration.Response());
            } else {
                EventBus.getDefault().post(new FcmTokenRegistration.Failure());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to registerUserToFcm.", e);
        }
    }

}
