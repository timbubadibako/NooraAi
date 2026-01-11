// app/src/main/java/com/example/nooraai/supabase/SupabaseClientProvider.kt
package com.example.nooraai.supabase

import com.example.nooraai.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    // singleton supabase client, reuse across app
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY // or publishable key
        ) {
            // install modules you need
            install(Postgrest)
            // if your client requires explicit auth install uncomment:
            // install(Gotrue)
        }
    }
}