# Profit planner

Available in **Overview → Plan a profitable opening** and **More → Profit planner**.

Choose a future service date, an existing mapped appointment and one free opening within the owner-entered working hours. The planner uses that appointment's service, ZIP and map pin to find nearby customers who already pass RouteRevive's repeat-service and contact-permission rules. Customers without valid pins cannot be included; the screen reports how many otherwise-eligible customers need a pin. A planned offer never expands to the entire ZIP's audience.

## What the estimate means

The owner enters a whole-crew hourly cost, materials per added job, vehicle cash cost per added job, service minutes and added travel/setup minutes. All three cost fields require a value, including an explicit zero where a cost does not apply. The last saved plan for the same service supplies editable starting assumptions on a later visit.

- Reserved minutes per added booking = service minutes + travel/setup minutes.
- Labor cost = crew hourly cost × reserved minutes ÷ 60, rounded **up** to the next cent.
- Entered cost per job = labor + materials + vehicle cost.
- Offer price = regular starting price × (100 − discount %) ÷ 100, rounded to cents using half-up rounding.
- Contribution per job = offer price − entered cost.
- Contribution margin = contribution ÷ offer price × 100.
- Minimum agreed price = entered cost ÷ (1 − target margin), rounded **up** to the next cent.

The comparison shows contribution at full price, contribution with the proposed discount, the price floor and the largest whole-number discount within the existing 0–50% campaign limit that still meets the target. A below-target offer cannot be created or booked through this workflow.

Example: 60 service minutes, 15 travel/setup minutes, a $60 whole-crew hourly cost, $10 materials and $5 vehicle cost gives $90 entered cost. A $150 regular price with 10% off gives a $135 customer price and $45 contribution. A 30% target requires at least **$128.58**; the maximum whole-number discount is **14%**.

These figures are **contribution estimates before overhead and tax**, based on owner-supplied costs. They are not net profit, collected payments or a prediction that customers will book. The full-slot total is explicitly a hypothetical full-booking scenario. No receipt or financial-report totals are changed by planning.

## Calendar and travel rules

Openings merge overlapping appointments, release canceled appointments and account for previous-day work crossing midnight. Invalid appointment dates/times/durations prevent a misleading availability estimate. Each opening is considered independently. Capacity is the smaller of the eligible nearby audience and the number of complete service/travel blocks that fit, capped at 99.

The radius and displayed distances are **straight-line proximity**. There is no road-network travel-time, traffic, crew-routing or ETA prediction. The owner must enter sufficient added travel/setup time, including travel to the next existing stop, and verify the real drive before sending an offer.

Drafts do not reserve time. Offer creation rechecks the opening, and confirmation rechecks actual schedule conflicts. A planned booking must stay within its saved opening, reserve its full service/travel block and meet its saved minimum agreed price. Rescheduling retains those restrictions and the planned service date. A changed or canceled anchor/pin, or a recipient moved outside the radius, blocks later message approval/sending until the owner reviews a new plan.

The generated draft expires the day before service. Each message still requires review and approval; sending remains an explicit action in the owner's SMS app.

## Persistence and compatibility

Each planned campaign stores optional `profitPlan` assumptions in the existing encrypted record and full-backup format. Older campaigns without this field retain their existing booking behavior. Old archives still import. New archives validate every present planning object before replacing local records, rejecting malformed objects, invalid costs, fractional minute counts and invalid ranges. Keep the updated app when restoring a backup containing planned offers; older app versions do not enforce these new restrictions.

The Android application ID and signing configuration remain unchanged. Version 0.2.7 uses version code 18. Release-signed in-place upgrades, full photo-inclusive archive restore and configured cloud accounts remain separate device release checks.
