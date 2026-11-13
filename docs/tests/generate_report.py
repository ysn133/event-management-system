from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


OUTPUT = Path(__file__).with_name("rapport-tests-unitaires-prestataires.pdf")
FONT_DIR = Path(r"C:\Windows\Fonts")
pdfmetrics.registerFont(TTFont("Arial", str(FONT_DIR / "arial.ttf")))
pdfmetrics.registerFont(TTFont("Arial-Bold", str(FONT_DIR / "arialbd.ttf")))

NAVY = colors.HexColor("#19324A")
TEAL = colors.HexColor("#187C76")
LIGHT = colors.HexColor("#EDF4F5")
LINE = colors.HexColor("#D6E0E5")
MUTED = colors.HexColor("#5F7180")
GREEN = colors.HexColor("#147D53")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="CoverTitle", fontName="Arial-Bold", fontSize=26, leading=32,
    textColor=NAVY, alignment=1, spaceAfter=8,
))
styles.add(ParagraphStyle(
    name="CoverSubtitle", fontName="Arial", fontSize=13, leading=19,
    textColor=MUTED, alignment=1,
))
styles.add(ParagraphStyle(
    name="Heading1Custom", fontName="Arial-Bold", fontSize=16, leading=20,
    textColor=NAVY, spaceBefore=8, spaceAfter=9, keepWithNext=True,
))
styles.add(ParagraphStyle(
    name="Heading2Custom", fontName="Arial-Bold", fontSize=11, leading=15,
    textColor=TEAL, spaceBefore=6, spaceAfter=5, keepWithNext=True,
))
styles.add(ParagraphStyle(
    name="BodyCustom", fontName="Arial", fontSize=9, leading=13,
    textColor=colors.HexColor("#263746"), spaceAfter=6,
))
styles.add(ParagraphStyle(
    name="SmallCustom", fontName="Arial", fontSize=7.5, leading=10,
    textColor=MUTED,
))
styles.add(ParagraphStyle(
    name="TableCustom", fontName="Arial", fontSize=7.1, leading=9,
    textColor=colors.HexColor("#263746"),
))
styles.add(ParagraphStyle(
    name="TableHeaderCustom", fontName="Arial-Bold", fontSize=7.2,
    leading=9, textColor=colors.white,
))
styles.add(ParagraphStyle(
    name="StatusCustom", fontName="Arial-Bold", fontSize=7.2, leading=9,
    textColor=GREEN, alignment=1,
))


def p(text, style="BodyCustom"):
    return Paragraph(escape(str(text)).replace("\n", "<br/>"), styles[style])


def table_style(header=True, striped=False):
    rules = [
        ("GRID", (0, 0), (-1, -1), 0.4, LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]
    if header:
        rules.append(("BACKGROUND", (0, 0), (-1, 0), NAVY))
    if striped:
        rules.append(("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, LIGHT]))
    return TableStyle(rules)


def footer(canvas, doc):
    canvas.saveState()
    width, _ = A4
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.5)
    canvas.line(18 * mm, 15 * mm, width - 18 * mm, 15 * mm)
    canvas.setFont("Arial", 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(18 * mm, 10 * mm, "Dossier de tests unitaires — Module Prestataires")
    canvas.drawRightString(width - 18 * mm, 10 * mm, f"Page {doc.page}")
    canvas.restoreState()


test_cases = [
    ("UT-PRE-01", "Rejeter création avec nom vide et champs incomplets", "HTTP 400; aucun appel DB"),
    ("UT-PRE-02", "Rejeter un identifiant non numérique", "HTTP 400; aucun appel DB"),
    ("UT-PRE-03", "Résoudre recherche avant la route paramétrée; convertir les nombres PostgreSQL", "HTTP 200; paramètres et modèle corrects"),
    ("UT-PRE-04", "Refuser la suppression d’un prestataire référencé", "HTTP 409; ne pas supprimer"),
    ("UT-PRE-05", "Consulter le détail et ses services à description nulle", "HTTP 200; structure et types corrects"),
    ("UT-PRE-06", "Créer un prestataire valide et nettoyer les chaînes", "HTTP 201; SQL paramétré"),
    ("UT-PRE-07", "Rendre coordonnées et type de service obligatoires", "HTTP 400; aucun appel DB"),
    ("UT-PRE-08", "Refuser un tarif négatif ou non numérique", "HTTP 400; aucun appel DB"),
    ("UT-PRE-09", "Refuser un tarif supérieur à NUMERIC(12,2)", "HTTP 400 avant INSERT"),
    ("UT-PRE-10", "Accepter zéro et le maximum 9 999 999 999,99", "HTTP 201; valeur retournée sans débordement"),
    ("UT-PRE-11", "Lister les prestataires et mapper les résultats SQL", "HTTP 200; liste JSON attendue"),
    ("UT-PRE-12", "Filtrer par nom et type avec valeurs SQL paramétrées", "HTTP 200; filtres normalisés"),
    ("UT-PRE-13", "Consulter un prestataire absent", "HTTP 404 au format erreur"),
    ("UT-PRE-14", "Modifier un prestataire existant", "HTTP 200; UPDATE paramétré"),
    ("UT-PRE-15", "Modifier un prestataire absent", "HTTP 404"),
    ("UT-PRE-16", "Supprimer un prestataire absent", "HTTP 404; arrêt après vérification"),
    ("UT-PRE-17", "Supprimer un prestataire sans contrat lié", "HTTP 204 vide; suppression appelée"),
    ("UT-PRE-18", "Créer un service d’un prestataire existant", "HTTP 201; association et tarif corrects"),
    ("UT-PRE-19", "Créer un service sans description", "HTTP 201; description null"),
    ("UT-PRE-20", "Refuser un tarif négatif de service", "HTTP 400 avant appel DB"),
    ("UT-PRE-21", "Refuser un tarif de service supérieur à NUMERIC(12,2)", "HTTP 400 avant appel DB"),
    ("UT-PRE-22", "Refuser un service si le prestataire est absent", "HTTP 404; aucun INSERT"),
    ("UT-PRE-23", "Lister les services d’un prestataire existant", "HTTP 200; liste convertie"),
    ("UT-PRE-24", "Lister les services d’un prestataire absent", "HTTP 404"),
    ("UT-PRE-25", "Modifier un service rattaché au bon prestataire", "HTTP 200; mise à jour contrainte par les deux ids"),
    ("UT-PRE-26", "Modifier un service absent ou non rattaché", "HTTP 404"),
    ("UT-PRE-27", "Supprimer un service existant", "HTTP 204 vide; ids SQL corrects"),
    ("UT-PRE-28", "Supprimer un service absent", "HTTP 404"),
    ("UT-PRE-29", "Rejeter un JSON mal formé", "HTTP 400 contrôlé"),
    ("UT-PRE-30", "Répondre de façon homogène à une route inconnue", "HTTP 404 JSON"),
    ("UT-PRE-31", "Mapper une violation de clé étrangère PostgreSQL (23503)", "HTTP 409 JSON"),
    ("UT-PRE-32", "Mapper un dépassement numérique PostgreSQL (22003)", "HTTP 400 contrôlé"),
    ("UT-PRE-33", "Masquer les détails d’une erreur DB inattendue", "HTTP 500 générique sans fuite interne"),
]

story = [
    Spacer(1, 25 * mm),
    p("DOSSIER DE TESTS UNITAIRES", "CoverTitle"),
    p("Module de gestion des prestataires", "CoverSubtitle"),
    Spacer(1, 7 * mm),
]

cover = [
    [p("Élément", "TableHeaderCustom"), p("Valeur", "TableHeaderCustom")],
    [p("Nom du projet", "TableCustom"), p("Event Management System", "TableCustom")],
    [p("Référence du document", "TableCustom"), p("TU-PREST-001", "TableCustom")],
    [p("Version du dossier", "TableCustom"), p("1.0", "TableCustom")],
    [p("Date d’exécution", "TableCustom"), p("7 novembre 2026", "TableCustom")],
    [p("Branche de travail", "TableCustom"), p("hamzakun57-prestataires-setup (basée sur prestataires)", "TableCustom")],
    [p("Révision de base", "TableCustom"), p("2a3c6ac — changements de test non commités", "TableCustom")],
    [p("Statut", "TableCustom"), p("Exécuté — 33 tests réussis, 0 échec", "TableCustom")],
]
t = Table(cover, colWidths=[53 * mm, 119 * mm], repeatRows=1)
t.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), NAVY),
    ("BACKGROUND", (0, 1), (0, -1), LIGHT),
    ("GRID", (0, 0), (-1, -1), 0.45, LINE),
    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ("LEFTPADDING", (0, 0), (-1, -1), 8),
    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
    ("TOPPADDING", (0, 0), (-1, -1), 8),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
]))
story += [
    t, Spacer(1, 9 * mm),
    p("Dossier établi sur la base de l’exemple fourni, avec une structure et des scénarios adaptés au code réel du module Prestataires.", "SmallCustom"),
    PageBreak(),
    p("1. Historique du document", "Heading1Custom"),
]

history = [
    [p(x, "TableHeaderCustom") for x in ["Version", "Date", "Intervenant / rôle", "Modification", "Validation"]],
    [p(x, "TableCustom") for x in ["0.1", "07/11/2026", "Développement", "Création du dossier et définition du périmètre.", "À relire"]],
    [p(x, "TableCustom") for x in ["1.0", "07/11/2026", "Développement / test", "Scénarios Node.js, exécution et consignation des résultats.", "Résultats exécutés"]],
]
t = Table(history, colWidths=[16 * mm, 25 * mm, 38 * mm, 65 * mm, 28 * mm], repeatRows=1)
t.setStyle(table_style())
story += [
    t,
    p("La validation formelle du responsable de projet, du testeur et de l’encadrant reste à compléter.", "SmallCustom"),
    p("2. Objet et objectifs", "Heading1Custom"),
    p("Ce dossier décrit les tests automatisés du backend Node.js/Express du module Prestataires. Il vérifie les réponses HTTP, la validation des données et les appels SQL préparés en remplaçant le pool PostgreSQL par un faux pool contrôlé. Les tests se rejouent sans écrire dans une base réelle.", "BodyCustom"),
    p("Objectifs : vérifier les opérations CRUD des prestataires et services, la recherche par nom/type, les validations et codes d’erreur, la règle de non-suppression si un prestataire est référencé, et le comportement face aux erreurs SQL.", "BodyCustom"),
    p("3. Présentation du module et règles couvertes", "Heading1Custom"),
    p("Le service expose des routes REST sous /api/prestataires. Un prestataire comprend un identifiant, un nom, des coordonnées, un type de service et un tarif. Chaque service associé comprend un nom, une description facultative et un tarif. Les tarifs sont positifs ou nuls et limités par PostgreSQL NUMERIC(12,2).", "BodyCustom"),
]

rules = [
    ("RG01", "Identifiant généré et unique par la base"),
    ("RG02", "Nom, coordonnées, type de service et tarif obligatoires"),
    ("RG03", "Créer, consulter, modifier et supprimer un prestataire"),
    ("RG04–RG05", "Services liés au prestataire, avec tarif"),
    ("RG06", "Tarif positif/nul, borné à NUMERIC(12,2)"),
    ("RG07", "Recherche filtrée par nom et/ou type de service"),
    ("RG08", "Modification transmise à la base par requête UPDATE"),
    ("RG09", "Suppression refusée si événement/réservation référence le prestataire"),
    ("RG10", "Persistance PostgreSQL structurée; non testée en direct dans cette suite"),
]
rule_data = [[p("Règle", "TableHeaderCustom"), p("Couverture / réserve", "TableHeaderCustom")]]
rule_data += [[p(a, "TableCustom"), p(b, "TableCustom")] for a, b in rules]
t = Table(rule_data, colWidths=[27 * mm, 145 * mm], repeatRows=1)
t.setStyle(table_style(striped=True))
story += [
    t,
    p("4. Périmètre et méthode", "Heading1Custom"),
    p("Outils : Node.js node:test et node:assert/strict, serveur Express sur port éphémère, requêtes HTTP locales via fetch, pool PostgreSQL remplacé par un double de test contrôlant les réponses et enregistrant les paramètres SQL. Chaque scénario prépare le double, appelle une route et vérifie le statut, le JSON et/ou la requête.", "BodyCustom"),
    p("Ces tests isolent les routes du vrai PostgreSQL. Ils vérifient le contrat HTTP et la logique des routes, mais ne constituent pas une validation d’intégration de la base ni une mesure de couverture de code.", "BodyCustom"),
    p("Exemple détaillé — création d’un prestataire valide", "Heading2Custom"),
    p("Préparer : injecter un faux pool qui retourne une ligne SQL où id et tarif sont des chaînes. Exécuter : envoyer POST /api/prestataires avec nom, contact, typeService et tarif JSON valides. Vérifier : HTTP 201, nombres convertis dans le JSON, et paramètres SQL avec les chaînes nettoyées. La base réelle n’est pas contactée.", "BodyCustom"),
    PageBreak(),
    p("5. Tableau récapitulatif des cas de test", "Heading1Custom"),
    p("Résultats obtenus lors de l’exécution du 7 novembre 2026. Chaque scénario a réussi.", "BodyCustom"),
]

case_rows = [[p(x, "TableHeaderCustom") for x in ["ID", "Scénario", "Résultat attendu", "Résultat"]]]
for ident, scenario, expected in test_cases:
    case_rows.append([p(ident, "TableCustom"), p(scenario, "TableCustom"), p(expected, "TableCustom"), p("Réussi", "StatusCustom")])
t = Table(case_rows, colWidths=[22 * mm, 62 * mm, 60 * mm, 28 * mm], repeatRows=1)
t.setStyle(table_style(striped=True))
story += [
    t,
    PageBreak(),
    p("6. Environnement et procédure d’exécution", "Heading1Custom"),
]

environment = [
    [p("Élément", "TableHeaderCustom"), p("Valeur réellement observée", "TableHeaderCustom")],
    [p("Runtime", "TableCustom"), p("Node.js v24.20.0", "TableCustom")],
    [p("Serveur HTTP", "TableCustom"), p("Express 4 (service existant)", "TableCustom")],
    [p("Framework de test", "TableCustom"), p("node:test et node:assert/strict (intégrés à Node.js)", "TableCustom")],
    [p("Base pendant les tests", "TableCustom"), p("Aucune connexion réelle — faux pool injecté", "TableCustom")],
    [p("Fichiers testés", "TableCustom"), p("services/prestataires/server.js et services/prestataires/test/server.test.js", "TableCustom")],
    [p("Commande exécutée", "TableCustom"), p("npm test --prefix services/prestataires -- --test-reporter spec", "TableCustom")],
    [p("Branche / base", "TableCustom"), p("hamzakun57-prestataires-setup; révision de base 2a3c6ac", "TableCustom")],
]
t = Table(environment, colWidths=[54 * mm, 118 * mm], repeatRows=1)
t.setStyle(table_style())
story += [
    t,
    p("Pour rejouer les tests depuis la racine du dépôt :", "Heading2Custom"),
    p("cd services/prestataires<br/>npm test", "BodyCustom"),
    p("7. Bilan d’exécution", "Heading1Custom"),
]

summary = [
    [p("Indicateur", "TableHeaderCustom"), p("Résultat", "TableHeaderCustom")],
    [p("Tests exécutés", "TableCustom"), p("33", "TableCustom")],
    [p("Réussis", "TableCustom"), p("33", "TableCustom")],
    [p("Échoués / ignorés / bloqués", "TableCustom"), p("0 / 0 / 0", "TableCustom")],
    [p("Taux de réussite des tests exécutés", "TableCustom"), p("100 %", "TableCustom")],
    [p("Couverture de code instrumentée", "TableCustom"), p("Non mesurée", "TableCustom")],
]
t = Table(summary, colWidths=[92 * mm, 80 * mm], repeatRows=1)
t.setStyle(table_style(striped=True))
story += [
    t,
    Spacer(1, 4 * mm),
    p("Conclusion : tous les scénarios automatisés réussissent dans l’environnement isolé. Ce résultat ne prouve pas l’accès à PostgreSQL, l’application de la migration, ni la persistance réelle des données. Un test avec une base dédiée reste nécessaire avant validation de bout en bout.", "BodyCustom"),
    p("8. Anomalies, limites et validation", "Heading1Custom"),
    p("Anomalie ciblée : un dépassement de capacité NUMERIC PostgreSQL (SQLSTATE 22003) provoquait une erreur générique lors d’une création. Le backend valide maintenant la limite de NUMERIC(12,2) avant requête et transforme aussi SQLSTATE 22003 en réponse HTTP 400. Ces protections sont couvertes par UT-PRE-09, UT-PRE-10, UT-PRE-21 et UT-PRE-32. Redémarrer l’instance Node qui sert l’API avant de retester.", "BodyCustom"),
    p("Limites : le pool SQL est simulé; aucune base de test dédiée n’était configurée. Le comportement lié aux contrats/réservations est vérifié avec des réponses simulées, pas avec une table réelle. Aucun taux de couverture du code n’a été calculé. Le code et le dossier sont des changements locaux non commités à la date du rapport.", "BodyCustom"),
    p("Validation formelle (à compléter)", "Heading2Custom"),
]

signatures = [
    [p(x, "TableHeaderCustom") for x in ["Rôle", "Nom", "Décision / commentaire", "Date / signature"]],
    [p(x, "TableCustom") for x in ["Développeur responsable", "À compléter", "À compléter", "À compléter"]],
    [p(x, "TableCustom") for x in ["Responsable des tests", "À compléter", "À compléter", "À compléter"]],
    [p(x, "TableCustom") for x in ["Chef de projet / encadrant", "À compléter", "À compléter", "À compléter"]],
]
t = Table(signatures, colWidths=[42 * mm, 38 * mm, 54 * mm, 38 * mm], rowHeights=[None, 16 * mm, 16 * mm, 16 * mm], repeatRows=1)
t.setStyle(table_style())
story += [
    t,
    Spacer(1, 4 * mm),
    p("Annexe — source des tests : services/prestataires/test/server.test.js", "SmallCustom"),
]

doc = SimpleDocTemplate(
    str(OUTPUT),
    pagesize=A4,
    rightMargin=18 * mm,
    leftMargin=18 * mm,
    topMargin=17 * mm,
    bottomMargin=21 * mm,
    title="Rapport de tests unitaires — Module Prestataires",
    author="Équipe de développement",
)
doc.build(story, onFirstPage=footer, onLaterPages=footer)
print(f"Rapport généré : {OUTPUT} ({OUTPUT.stat().st_size} octets, {len(test_cases)} cas documentés)")
