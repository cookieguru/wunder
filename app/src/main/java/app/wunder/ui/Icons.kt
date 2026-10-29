package app.wunder.ui

import app.wunder.R

/** Meteocons (MIT) icons by their web name. */
fun iconRes(name: String): Int = when (name) {
    "clear-day" -> R.drawable.wx_clear_day
    "clear-night" -> R.drawable.wx_clear_night
    "cloudy" -> R.drawable.wx_cloudy
    "drizzle" -> R.drawable.wx_drizzle
    "dust" -> R.drawable.wx_dust
    "fog" -> R.drawable.wx_fog
    "hail" -> R.drawable.wx_hail
    "haze" -> R.drawable.wx_haze
    "hurricane" -> R.drawable.wx_hurricane
    "moon-first-quarter" -> R.drawable.wx_moon_first_quarter
    "moon-full" -> R.drawable.wx_moon_full
    "moon-last-quarter" -> R.drawable.wx_moon_last_quarter
    "moon-new" -> R.drawable.wx_moon_new
    "moonrise" -> R.drawable.wx_moonrise
    "moonset" -> R.drawable.wx_moonset
    "moon-waning-crescent" -> R.drawable.wx_moon_waning_crescent
    "moon-waning-gibbous" -> R.drawable.wx_moon_waning_gibbous
    "moon-waxing-crescent" -> R.drawable.wx_moon_waxing_crescent
    "moon-waxing-gibbous" -> R.drawable.wx_moon_waxing_gibbous
    "mostly-clear-day" -> R.drawable.wx_mostly_clear_day
    "mostly-clear-night" -> R.drawable.wx_mostly_clear_night
    "overcast-day" -> R.drawable.wx_overcast_day
    "overcast-night" -> R.drawable.wx_overcast_night
    "partly-cloudy-day-rain" -> R.drawable.wx_partly_cloudy_day_rain
    "partly-cloudy-day-snow" -> R.drawable.wx_partly_cloudy_day_snow
    "partly-cloudy-day" -> R.drawable.wx_partly_cloudy_day
    "partly-cloudy-night-rain" -> R.drawable.wx_partly_cloudy_night_rain
    "partly-cloudy-night-snow" -> R.drawable.wx_partly_cloudy_night_snow
    "partly-cloudy-night" -> R.drawable.wx_partly_cloudy_night
    "raindrop" -> R.drawable.wx_raindrop
    "rain" -> R.drawable.wx_rain
    "sleet" -> R.drawable.wx_sleet
    "smoke" -> R.drawable.wx_smoke
    "snowflake" -> R.drawable.wx_snowflake
    "snow" -> R.drawable.wx_snow
    "sun-hot" -> R.drawable.wx_sun_hot
    "sunrise" -> R.drawable.wx_sunrise
    "sunset" -> R.drawable.wx_sunset
    "thunderstorms-day" -> R.drawable.wx_thunderstorms_day
    "thunderstorms-extreme" -> R.drawable.wx_thunderstorms_extreme
    "thunderstorms-night" -> R.drawable.wx_thunderstorms_night
    "thunderstorms" -> R.drawable.wx_thunderstorms
    "tornado" -> R.drawable.wx_tornado
    "wind-snow" -> R.drawable.wx_wind_snow
    "wind" -> R.drawable.wx_wind
    else -> R.drawable.wx_not_available
}
