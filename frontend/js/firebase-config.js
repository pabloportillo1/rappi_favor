// ══════════════════════════════════════════════════════════════
//  firebase-config.js — Configuración del cliente Firebase
//
//  ⚠️  REEMPLAZA los valores de abajo con los de tu proyecto:
//  Firebase Console → Tu proyecto → Configuración → Tus apps → SDK web
// ══════════════════════════════════════════════════════════════

const firebaseConfig = {
  apiKey:            "AIzaSyAfiMWmP_56kFX0-vYN3iz04qK3uXPo1cI",
  authDomain:        "rappifavor-48391.firebaseapp.com",
  databaseURL:       "https://rappifavor-48391-default-rtdb.firebaseio.com",
  projectId:         "rappifavor-48391",
  storageBucket:     "rappifavor-48391.firebasestorage.app",
  messagingSenderId: "950434016889",
  appId:             "1:950434016889:web:dc298154fa71119a0d2ea8"
};

firebase.initializeApp(firebaseConfig);
