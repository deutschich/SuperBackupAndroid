// Beispielhafte JavaScript-Funktionalität

document.addEventListener("DOMContentLoaded", function() {
  // Kontaktformular-Verarbeitung
  const contactForm = document.getElementById('contactForm');
  const formFeedback = document.getElementById('formFeedback');

  contactForm.addEventListener('submit', function(e) {
    e.preventDefault();

    // Formularwerte auslesen
    const name = document.getElementById('name').value;
    const email = document.getElementById('email').value;
    const message = document.getElementById('message').value;

    // Simuliere das Versenden des Formulars
    formFeedback.textContent = "Vielen Dank, " + name + "! Deine Nachricht wurde gesendet.";
    formFeedback.style.color = "green";

    // Formularfelder zurücksetzen
    contactForm.reset();
  });

  // Download-Button Event (nur Beispiel)
  // const downloadBtn = document.getElementById('downloadBtn');
  // downloadBtn.addEventListener('click', function(e) {
  //  e.preventDefault();
  //  alert("Der Download startet in Kürze...");
    // Beispiel: window.location.href = "pfad/zur/SuperBackupAndroid.apk";
  // });
});
