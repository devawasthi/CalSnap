-- Local demo only. Never run in production. Rerunnable without duplicate rows.
INSERT INTO users(id,email,name,oauth_provider,oauth_subject,timezone) VALUES
 ('00000000-0000-0000-0000-000000000001','demo@calsnap.local','Alex Morgan','demo','demo','Asia/Kolkata') ON CONFLICT DO NOTHING;
INSERT INTO goals(id,user_id,daily_calories,daily_protein_g,effective_from) VALUES
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',2000,120,'2020-01-01') ON CONFLICT DO NOTHING;
INSERT INTO food_logs(id,user_id,food_name,calories,protein_g,carbs_g,fat_g,quantity_multiplier,portion_g,logged_at) VALUES
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Avocado toast & poached eggs',385,19,32,20,1,220,date_trunc('day',now() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata' + interval '8 hours'),
 ('20000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Grilled chicken & quinoa bowl',520,42,48,17,1,350,date_trunc('day',now() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata' + interval '12 hours'),
 ('20000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','Greek yogurt with blueberries',165,16,20,3,1,180,date_trunc('day',now() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata' + interval '15 hours') ON CONFLICT DO NOTHING;
