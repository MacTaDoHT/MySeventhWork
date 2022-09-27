package MySeventhWork;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;

import static MySeventhWork.TemperatureConverter.FahrenheitCelsius;

public class AccuWeatherProvider implements WeatherProvider {

    private static final String BASE_HOST = "dataservice.accuweather.com";
    private static final String FORECAST_ENDPOINT = "forecasts";
    private static final String CURRENT_CONDITIONS_ENDPOINT = "currentconditions";
    private static final String API_VERSION = "v1";
    private static final String API_KEY = ApplicationGlobalState.getInstance().getApiKey();

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void getWeather(Periods periods) throws IOException {
        String cityKey = detectCityKey();
        if (periods.equals(Periods.NOW)) {
            HttpUrl url = new HttpUrl.Builder()
                    .scheme("http")
                    .host(BASE_HOST)
                    .addPathSegment(CURRENT_CONDITIONS_ENDPOINT)
                    .addPathSegment(API_VERSION)
                    .addPathSegment(cityKey)
                    .addQueryParameter("apikey", API_KEY)
                    .build();

            Request request = new Request.Builder()
                    .addHeader("accept", "application/json")
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            String rawBody = response.body().string();

            List<WeatherResponse> weatherResponse = objectMapper.readValue(
                    rawBody,
                    new TypeReference<>() {
                    });

            if (weatherResponse.isEmpty()) {
                throw new IOException("Result not found");
            }

            WeatherResponse currentConditionResponse = weatherResponse.get(0);

            System.out.printf("Town: %s, date: %s feel: \"%s\", temperature: %s %s\n\n",
                    ApplicationGlobalState.getInstance().getSelectedCity(),
                    currentConditionResponse.getLocalObservationDateTime(),
                    currentConditionResponse.getWeatherText(),
                    currentConditionResponse.getTemperature().getMetric().getValue(),
                    currentConditionResponse.getTemperature().getMetric().getUnit());

        } else if (periods.equals(Periods.FIVE_DAYS)) {
            HttpUrl url = new HttpUrl.Builder()
                    .scheme("http")
                    .host(BASE_HOST)
                    .addPathSegment(FORECAST_ENDPOINT)
                    .addPathSegment(API_VERSION)
                    .addPathSegment("daily")
                    .addPathSegment("5day")
                    .addPathSegment(cityKey)
                    .addQueryParameter("apikey", API_KEY)
                    .build();

            Request request = new Request.Builder()
                    .addHeader("accept", "application/json")
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            String rawBody = response.body().string();

            WeatherResponseArray weatherIn5DaysResponse = objectMapper.readValue(
                    rawBody,
                    WeatherResponseArray.class);

            List<DailyForecast> dailyForecasts = weatherIn5DaysResponse.getDailyForecasts();
            System.out.println("In town " + ApplicationGlobalState.getInstance().getSelectedCity());
            for (DailyForecast dailyForecast : dailyForecasts) {
                Temperature5Days temperature = dailyForecast.getTemperature();
                System.out.printf("On date %s day  \"%s\", night  \"%s\", temperature - from %s С to %s С\n",
                        dailyForecast.getDate(),
                        dailyForecast.getDay().getIconPhrase(),
                        dailyForecast.getNight().getIconPhrase(),
                        FahrenheitCelsius(temperature.getMinimum().getValue()),
                        FahrenheitCelsius(temperature.getMaximum().getValue()));
            }
            System.out.println();
        }


    }

    public String detectCityKey() throws IOException {
        String selectedCity = ApplicationGlobalState.getInstance().getSelectedCity();

        HttpUrl detectLocationURL = new HttpUrl.Builder()
                .scheme("http")
                .host(BASE_HOST)
                .addPathSegment("locations")
                .addPathSegment(API_VERSION)
                .addPathSegment("cities")
                .addPathSegment("autocomplete")
                .addQueryParameter("apikey", API_KEY)
                .addQueryParameter("q", selectedCity)
                .build();

        Request request = new Request.Builder()
                .addHeader("accept", "application/json")
                .url(detectLocationURL)
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("Невозможно прочесть информацию о городе. " +
                    "Код ответа сервера = " + response.code() + " тело ответа = " + response.body().string());
        }
        String jsonResponse = response.body().string();
        System.out.println("Произвожу поиск города " + selectedCity);

        if (objectMapper.readTree(jsonResponse).size() > 0) {
            String cityName = objectMapper.readTree(jsonResponse).get(0).get("/LocalizedName").asText();
            String countryName = objectMapper.readTree(jsonResponse).get(0).get("/Country/LocalizedName").asText();
            System.out.println("Найден город " + cityName + " в стране " + countryName);
        } else throw new IOException("Server returns 0 cities");

        return objectMapper.readTree(jsonResponse).get(0).get("/Key").asText();
    }
}