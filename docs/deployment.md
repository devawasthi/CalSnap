# Render free deployment

CalSnap deploys as one Render free web service plus one free Render PostgreSQL database. The single container runs the React frontend, Nginx and the Micronaut API, preserving same-origin cookies and OAuth callbacks while consuming only one pool of free web-service hours.

Meal photos use PostgreSQL instead of the container filesystem because Render free web-service disks are ephemeral. The database is suitable only for a temporary beta: Render free PostgreSQL is limited to 1 GB, expires 30 days after creation and has no managed backups.

## 1. Put CalSnap in its own Git repository

Render deploys from GitHub, GitLab or Bitbucket. The calsnap directory must be the repository root so render.yaml and the Docker build paths resolve correctly.

Do not commit .env files or credentials. The Blueprint asks Render to generate the JWT secret and prompts for the external API credentials.

## 2. Deploy the Blueprint

1. Sign in to Render and choose **New → Blueprint**.
2. Connect the CalSnap repository.
3. Confirm that Render detects render.yaml.
4. Review two free resources: the calsnap web service and calsnap-db PostgreSQL database, both in Singapore.
5. Supply OPENAI_API_KEY, USDA_API_KEY, GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET when prompted. Temporary placeholders are acceptable for the first deploy if the Google OAuth client does not exist yet.
6. Apply the Blueprint.

The first build compiles the frontend and Java backend. Render supplies the database host, port, name, user and password directly from the managed database; they are not copied into source control.

## 3. Finish Google sign-in

After Render assigns the public URL, create or update the Google OAuth web client with this exact authorized redirect URI:

~~~text
https://YOUR-RENDER-HOST.onrender.com/auth/callback
~~~

Replace the temporary GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET values in the Render service environment, then redeploy. CalSnap derives its public origin from RENDER_EXTERNAL_HOSTNAME, which Render injects automatically.

## 4. Verify the deployment

Open:

~~~text
https://YOUR-RENDER-HOST.onrender.com/health
~~~

It should return a JSON response with status UP. Then verify Google sign-in, create a goal, upload one meal, confirm it, reload the journal, view the saved photo and delete the entry.

The free service sleeps after 15 minutes without inbound traffic. The next request can take about a minute while it starts. Do not use synthetic traffic to defeat the free-plan sleep policy.

## Data and the 30-day database limit

Render's free PostgreSQL instance expires 30 days after creation. It becomes inaccessible at expiry and Render deletes it after the documented grace period unless it is upgraded. Put a calendar reminder on day 23.

Before expiry:

1. Decide whether to upgrade the database or migrate it to another PostgreSQL provider.
2. Temporarily allow only your current public IP in the database's inbound rules.
3. Use the external database URL shown in Render to export:

~~~sh
pg_dump --format=custom --no-owner --no-acl \
  "$RENDER_EXTERNAL_DATABASE_URL" > calsnap-before-expiry.dump
~~~

4. Remove the temporary inbound rule immediately after the export.
5. Test the dump by restoring it into a disposable PostgreSQL 16 database.

The database stores account data and meal photos, so its 1 GB limit includes both. CalSnap normalizes images before storage and limits users to five scans per hour on this deployment, but storage usage still needs monitoring.

## Free-plan boundaries

- One free web service gets 512 MB RAM and shares the workspace's 750 monthly free instance hours.
- The service spins down after 15 idle minutes and has cold starts.
- The local filesystem is ephemeral.
- Free PostgreSQL is 1 GB, expires after 30 days and has no Render backups.
- GPT-5 nano calls are billed separately by OpenAI.
- This configuration is appropriate for a temporary personal beta, not durable production.

Render documentation: [free services](https://render.com/docs/free), [Blueprints](https://render.com/docs/infrastructure-as-code), and [environment variables](https://render.com/docs/configure-environment-variables).
