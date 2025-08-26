# Pro Golf Coach Tracker (One-Week, Per-Shot Logging)

**What it does**
- Import a *weekly plan* JSON (days + drills).
- Log **every shot** for each drill (proximity cm, make/miss, or time sec).
- Export a **results JSON** that contains plan + all shots so you can send it back to your coach (me).
- Reset week when you're ready to load a new plan.

**Build (Android Studio, no command line needed)**
1. Open Android Studio Hedgehog or newer.
2. **File → Open…** and select the `ProGolfCoachTracker` folder.
3. Let Gradle sync. Then **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. When it finishes, click the notification to locate the APK and install it on your phone.

**How to use**
1. Launch the app → **Plan** tab → tap **Import Weekly Plan (.json)**.
2. Switch to **Track** tab to log shots per drill/day.
3. Go to **Export** tab → **Save to File** or **Share (copy/email)** to send me the week’s JSON.
4. Tap **Reset Week** when you’re ready to load a new plan.

**Plan JSON schema (kotlinx.serialization)**
```json
{
  "weekStartDate": "yyyy-MM-dd",
  "weekLabel": "Week 12 – XYZ",
  "days": [
    {
      "name": "Monday",
      "drills": [
        {
          "id": "unique-id",
          "name": "Drill Name",
          "club": "7i",
          "targetDesc": "How to run the drill / target",
          "scoringType": "PROXIMITY_CM | MAKE_MISS | TIME_SEC",
          "shotsPlanned": 10,
          "notes": "optional coach notes"
        }
      ]
    }
  ]
}
```

**Results JSON** (what you export) embeds this `plan` plus `days[].drills[].shots[]`:
```json
{
  "plan": { /* WeeklyPlan */ },
  "days": [
    {
      "name": "Monday",
      "drills": [
        {
          "drillId": "unique-id",
          "shots": [
            {
              "index": 1,
              "valueNumber": 120.0, // proximity cm OR time sec
              "valueBool": null,    // make/miss (true/false) when applicable
              "timestamp": 1690000000000,
              "note": "optional"
            }
          ]
        }
      ]
    }
  ]
}
```

**Sample plan provided** in `sample_weekly_plan.json`. Start with that, then replace with your weekly file I give you.
