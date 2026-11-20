package com.asoft.ios;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Set up's iOS 26 and watchOS from Apple: a section for each (SetupSection: state in one line, the one action, the GPU check
 * as the line they both need, Details closed), and under them "This setup", the one run's download, space and steps, which
 * exists while a run does. The GPU check comes first (no download without a pass; a failure says the GPU is not supported yet
 * and offers its log to copy or share). The choice of both at once is a box in Details; the shared part is done once.
 */
final class Setup26Screen {
    private static final int GRAY = Ui.DIM, RED = Ui.RED, GREEN = Ui.GREEN, ORANGE = Ui.ORANGE;

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("kernelcache.research.vphone600", "cloudOS research kernel");
        NAMES.put("Firmware/sptm.vresearch1.release.im4p", "SPTM");
        NAMES.put("Firmware/txm.iphoneos.research.im4p", "TXM");
        NAMES.put("Firmware/all_flash/DeviceTree.vphone600ap.im4p", "Device tree");
        NAMES.put("094-39914-031.dmg", "cloudOS ramdisk");
        NAMES.put("094-39278-029.dmg.aea", "cloudOS system (the GPU driver)");
        NAMES.put("094-97246-090.dmg.aea", "iOS 26 itself");
        NAMES.put("094-95368-092.dmg.aea", "System libraries (SystemOS)");
        NAMES.put("094-96441-094.dmg", "Built-in apps (AppOS)");
        NAMES.put("141-08453-168.dmg.aea", "watchOS itself");
        NAMES.put("Firmware/all_flash/DeviceTree.n207sap.im4p", "Watch device tree");
    }

    /** One system's size on the disk, measured once off the UI thread when it turns up set up. */
    private static final class Sized {
        volatile String text;
        boolean measuring, was;
    }

    private final Activity a;
    private final LinearLayout root;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final SetupSection s26, sw, run;
    private final Sized size26 = new Sized(), sizeWatch = new Sized();
    private TextView gpu, total, memory;
    private LinearLayout gpuButtons, files, steps;
    private View errorButtons;
    private ProgressBar totalBar;
    private CheckBox alsoWatch, alsoIos26, useIos27;
    private TextView primary26, primaryWatch, cancel;
    private final Map<String, TextView[]> fileRows = new LinkedHashMap<>();
    private final Map<String, ProgressBar> fileBars = new LinkedHashMap<>();
    private final Map<String, TextView> stepRows = new LinkedHashMap<>();
    /** What a tap on a system's action had to say (iOS runs, another install runs), under that system. */
    private final Map<String, String> said = new LinkedHashMap<>();
    private volatile GpuCheck.Result gpuResult;
    private String builtFor = "";

    Setup26Screen(Activity a) {
        this.a = a;
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        gpuResult = GpuCheck.dir(a).isDirectory() ? GpuCheck.last(a) : null;
        s26 = new SetupSection(a, "iOS 26", DeviceView.ISLAND, new int[] {0xff5a9bff, 0xff4a5df0, 0xff5b36cf});
        sw = new SetupSection(a, "watchOS", DeviceView.WATCH, null);
        run = new SetupSection(a, "This setup", -1, null);
        build();
        tick();
    }

    View view() {
        if (root.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) root.getParent()).removeView(root);
        }
        return root;
    }

    /** The view of the iOS 26 section, for the page to scroll to. */
    View ios26Section() {
        return s26.root;
    }

    private TextView text(LinearLayout to, String s, float size, int color) {
        return Ui.text(a, to, s, size, color);
    }

    private void build() {
        root.addView(s26.root);
        Ui.line(a, root, 22, 22);
        root.addView(sw.root);
        Ui.line(a, root, 22, 22);
        root.addView(run.root);

        // iOS 26: the GPU check is its first line, and the watch's too (they draw through the same GPU)
        gpu = text(s26.main, "", 13.5f, Ui.TEXT);
        gpu.setLineSpacing(Ui.dp(a, 1.5f), 1f);
        gpu.setPadding(0, Ui.dp(a, 8), 0, 0);
        gpuButtons = new LinearLayout(a);
        gpuButtons.setOrientation(LinearLayout.HORIZONTAL);
        s26.main.addView(gpuButtons);

        primary26 = Ui.capsule(a, "Set up", false, v -> onGo(PhoneSetup26.IOS26));
        s26.actions.addView(primary26, new LinearLayout.LayoutParams(-2, -2));
        primaryWatch = Ui.capsule(a, "Set up", false, v -> onGo(PhoneSetup26.WATCH));
        sw.actions.addView(primaryWatch, new LinearLayout.LayoutParams(-2, -2));

        s26.paragraph("iOS 26.6.2 and watchOS 27.2 run on the cloudOS 26.4 research kernel and draw through the phone's GPU. "
                + "The phone downloads Apple's files itself and builds their disks as the PC installer does: iOS 26 is about "
                + "10 GB from Apple and several hours of work, watchOS about 5 GB and about an hour, and their shared part "
                + "1 GB once. Keep the phone on its charger and on Wi-Fi.", GRAY);
        alsoWatch = check(s26.body, "Set up watchOS in the same run");
        // iOS 27.0.1 (experimental): the same steps with iOS 27's files (os.ios27 over os.ios26 in the manifest)
        useIos27 = check(s26.body, "iOS 27.0.1 instead of 26.6.2 (experimental, about 12 GB from Apple)");
        useIos27.setChecked(PhoneSetup26.ios27(a));
        sw.paragraph("watchOS 27.2 for an Apple Watch Series 9. It uses the same cloudOS files as iOS 26 (about 1 GB, downloaded "
                + "once) and the same GPU check.", GRAY);
        alsoIos26 = check(sw.body, "Set up iOS 26 in the same run");

        // This setup: the run's download, its steps, the room
        cancel = Ui.link(a, "Cancel", RED, v -> new android.app.AlertDialog.Builder(a)
                .setTitle("Cancel the install?")
                .setMessage("What was downloaded and built for it is deleted (an installed iOS 26 or watchOS stays).")
                .setPositiveButton("Cancel install", (d, w) -> SetupService.cancel(a))
                .setNegativeButton("Keep going", null)
                .show());
        run.actions.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        errorButtons = SetupScreen.logButtons(a, run.main, () -> SetupService.state.record);
        run.heading("Download");
        total = text(run.body, "", 15, Ui.TEXT);
        totalBar = run.bar(run.body, 4);
        files = new LinearLayout(a);
        files.setOrientation(LinearLayout.VERTICAL);
        run.body.addView(files, Ui.top(Ui.dp(a, 6)));
        run.heading("Steps");
        steps = new LinearLayout(a);
        steps.setOrientation(LinearLayout.VERTICAL);
        run.body.addView(steps);
        memory = text(run.body, "", 13, GRAY);
        memory.setPadding(0, Ui.dp(a, 10), 0, 0);
    }

    private CheckBox check(LinearLayout to, String s) {
        CheckBox b = new CheckBox(a);
        b.setText(s);
        b.setTextColor(Ui.TEXT);
        b.setTextSize(14.5f);
        to.addView(b, Ui.top(Ui.dp(a, 8)));
        return b;
    }

    private boolean mine() {
        return "a26".equals(SetupService.state.target);
    }

    private static String name(String group) {
        return PhoneSetup26.WATCH.equals(group) ? "watchOS" : "iOS 26";
    }

    private void tell(String group, String s) {
        said.put(group, s);
    }

    /** The action of @group's section: Set up, Resume, Pause, or the GPU check first. */
    private void onGo(String group) {
        SetupService.State s = SetupService.state;
        said.remove(group);
        boolean inRun = SetupState.plan26(a, group, false) != null;
        if (s.running && mine() && inRun) {
            if (s.paused) {
                SetupService.startA26(a);
            } else {
                SetupService.pause(a);
            }
            return;
        }
        if (s.running) {
            tell(group, "Another install is running: let it finish first.");
            return;
        }
        if (Guest.running()) {
            tell(group, "Close iOS first (side bar > Saves > Save and close): the setup needs its memory and its QEMU.");
            return;
        }
        if (!GpuCheck.passed(a)) {
            runCheck();
            return;
        }
        // (a begun run goes on with what it was asked for; a new one is this system, and the other too when its box is ticked)
        if (!PhoneSetup26.pending(a, group)) {
            boolean ios = PhoneSetup26.IOS26.equals(group);
            String other = ios ? PhoneSetup26.WATCH : PhoneSetup26.IOS26;
            boolean withOther = (ios ? alsoWatch : alsoIos26).isChecked() && !PhoneSetup26.installed(a, other)
                    || PhoneSetup26.pending(a, other);
            PhoneSetup26.setWants(a, ios || withOther && !ios, !ios || withOther && ios);
            if (ios || withOther) {
                PhoneSetup26.setIos27(a, useIos27.isChecked());
            }
        }
        // (the helper VM takes memory too: say so when little is free, K5)
        LowMemory.askBeforeStart(a, "the setup", Helper.needBytes(Helper.ramMiB(a, Helper.FLOOR_WORK_IN_RAM)),
                () -> Battery.askOnce(a, "The setup takes over an hour", true, () -> SetupService.startA26(a)));
    }

    private void runCheck() {
        if (GpuCheck.running) {
            return;
        }
        gpu.setText("Checking this phone's GPU… (about 10 s)");
        gpu.setTextColor(Ui.TEXT);
        new Thread(() -> {
            GpuCheck.Result r = GpuCheck.run(a);
            gpuResult = r;
            a.runOnUiThread(() -> builtFor = "");
        }, "gpucheck-run").start();
    }

    private TextView gpuLink(String s, View.OnClickListener l) {
        TextView t = Ui.link(a, s, Ui.LINK, l);
        t.setPadding(0, Ui.dp(a, 9), Ui.dp(a, 18), Ui.dp(a, 9));
        gpuButtons.addView(t);
        return t;
    }

    /** The GPU line: the check not made yet, running, passed (with its warnings) or failed (with why, and its log). */
    private void gpuCard() {
        GpuCheck.Result r = gpuResult;
        String key = GpuCheck.running ? "running" : r == null ? "none" : r.pass + (GpuCheck.passed(a) ? "p" : "");
        if (key.equals(builtFor)) {
            return;
        }
        builtFor = key;
        gpuButtons.removeAllViews();
        if (GpuCheck.running) {
            gpu.setText("Checking this phone's GPU… (about 10 s)");
            gpu.setTextColor(Ui.TEXT);
            return;
        }
        if (r == null || !r.ran || r.pass && !GpuCheck.passed(a)) {
            // (a pass of another build of the app, or of its Turnip, does not count: checked again)
            gpu.setText("First, a short check that this phone's GPU can run them (a few tests with Vulkan, nothing is downloaded).");
            gpu.setTextColor(Ui.TEXT);
            return;                     // the section's own button says "Check the GPU" (onGo)
        }
        if (r.pass && GpuCheck.passed(a)) {
            String warn = GpuCheck.ramWarning(a);
            String head = "✓ " + r.summary;
            // a pass in the compatibility mode (GpuCheck.noSparse): said in amber right under the green line
            // (on the phone's own driver, GpuCheck.systemDriver, the line says what is true there: the Turnip sentence
            // below is about the app's own driver, which does not run)
            String compat = GpuCheck.systemDriver(a) ? "\nExperimental: iOS 26 and watchOS will run on this phone's own graphics driver, which we could"
                    + " not test on this GPU. They may be slow, draw wrong pictures or stop. Send a report from Logs either way: it helps."
                    : r.nosparse ? "\nThis GPU's driver cannot do sparse binds from host memory, so iOS 26 and watchOS run in"
                    + " a compatibility mode that can stop after long use. The app saves before that when it can." : "";
            android.text.SpannableString t = new android.text.SpannableString(head + compat
                    + (warn.isEmpty() ? "" : "\n\n" + warn) + (r.slow.isEmpty() ? "" : "\n\n" + r.slow));
            if (!compat.isEmpty()) {
                t.setSpan(new android.text.style.ForegroundColorSpan(ORANGE), head.length(), head.length() + compat.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            gpu.setText(t);
            gpu.setTextColor(warn.isEmpty() ? GREEN : ORANGE);
            return;
        }
        StringBuilder t = new StringBuilder(r.summary);
        if (!r.why.isEmpty()) {
            t.append("\n\nIt needs:");
            for (String w : r.why) {
                t.append("\n• ").append(w);
            }
        }
        if (!r.slow.isEmpty()) {
            t.append("\n\n").append(r.slow);
        }
        t.append(r.never
                ? "\n\nThere is no mode without the GPU, so they cannot be installed here. You can still send us the check's log: "
                        + "it helps us know which phones people have."
                : "\n\n" + CrashText.GPU_KNOWN + " The check's log below says what this GPU's driver lacks, if you want to send it.");
        gpu.setText(t.toString());
        gpu.setTextColor(ORANGE);
        if (!r.never) {
            String family = CrashText.gpuFamily(CrashText.gpuName(r.report));
            gpuLink(family.isEmpty() ? "See the GPU issues" : "See the " + family + " issues",
                    v -> CrashReport.openGpuIssues(a, family));
        }
        gpuLink("Copy log", v -> {
            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("ASoftware GPU check", r.report));
            android.widget.Toast.makeText(a, "The log is copied", android.widget.Toast.LENGTH_SHORT).show();
        });
        gpuLink("Share log", v -> a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "ASoftware iOS GPU check: " + android.os.Build.MODEL)
                .putExtra(Intent.EXTRA_TEXT, r.report), "Send the GPU check's log")));
        gpuLink("Check again", v -> runCheck());
    }

    private static String gb(long b) {
        return SetupScreen.gb(b);
    }

    private void tick() {
        if (a.isFinishing() || a.isDestroyed()) {
            return;
        }
        update();
        h.postDelayed(this::tick, 500);
    }

    /** @group's size on the disk, for its state line: measured once it is set up (Installer.removal walks files). */
    private String sizeOf(String group, boolean installed) {
        Sized z = PhoneSetup26.WATCH.equals(group) ? sizeWatch : size26;
        if (installed && !z.was) {
            z.text = null;
        }
        z.was = installed;
        if (installed && z.text == null && !z.measuring) {
            z.measuring = true;
            int os = PhoneSetup26.WATCH.equals(group) ? Guest.WATCH : Guest.IOS26;
            new Thread(() -> {
                String t = SetupState.size(Installer.removal(a, os).bytes);
                z.text = t;
                z.measuring = false;
            }, "setup26-size").start();
        }
        return z.text == null ? "" : z.text;
    }

    /** One system's section. */
    private void section(SetupSection sec, TextView primary, String group, boolean pass, boolean failed) {
        SetupService.State s = SetupService.state;
        boolean watch = PhoneSetup26.WATCH.equals(group);
        boolean installed = PhoneSetup26.installed(a, group);
        SetupState.Plan live = SetupState.plan26(a, group, false);
        boolean inRun = live != null && !installed;
        boolean pending = !inRun && !installed && PhoneSetup26.pending(a, group);
        String progress = inRun ? SetupState.progress(live, s.pct) : pending ? SetupState.progress(SetupState.plan26(a, group, true), -1) : null;
        sec.setState(installed ? SetupSection.THERE : inRun ? SetupSection.WORKING : SetupSection.OFF,
                SetupState.line(installed, inRun, s.paused, progress, sizeOf(group, installed),
                        SetupState.day(SetupState.installedAt(a, watch ? Guest.WATCH : Guest.IOS26))));
        sec.device.show(null, installed, false);

        long fr = a.getFilesDir().getUsableSpace();
        long needFree = watch ? 20_000_000_000L : 46_000_000_000L;
        String info;
        if (installed) {
            info = "Start it from the home screen.";
        } else if (inRun) {
            info = s.text;
        } else if (watch && failed) {
            info = "This phone's GPU is not supported yet (see iOS 26).";
        } else if (watch && !pass) {
            info = "Needs the GPU check first (see iOS 26).";
        } else if (watch) {
            info = "About 5 GB from Apple, about an hour. Needs about 20 GB free while it builds, keeps about 4 GB.";
        } else {
            info = "About 10.2 GB from Apple, several hours. Needs about 46 GB free while it builds, keeps about 12 GB.";
        }
        SetupSection.say(sec.info, info, Ui.DIM);
        // the room is said outside the details: short of it, the install stops
        SetupSection.say(sec.extra, installed || inRun || watch && !pass || !watch && failed ? ""
                : String.format(Locale.ROOT, "%s free on this phone", gb(fr)), fr < needFree ? ORANGE : Ui.DIM);
        SetupSection.say(sec.error, said.getOrDefault(group, ""), RED);

        boolean other = s.running && !inRun;
        primary.setText(installed ? "" : inRun ? (s.paused ? "Resume" : "Pause") : !pass ? "Check the GPU" : pending ? "Resume" : "Set up");
        boolean noButton = installed || !inRun && failed || inRun && !s.paused && !SetupService.pausable(s.step);
        primary.setVisibility(noButton ? View.GONE : View.VISIBLE);
        primary.setEnabled(!GpuCheck.running && !other);
        primary.setAlpha(GpuCheck.running || other ? 0.4f : 1f);
        sec.follow(inRun);
        // both at once: only for a new run, and only while the other one is not there
        CheckBox also = watch ? alsoIos26 : alsoWatch;
        also.setVisibility(!PhoneSetup26.installed(a, watch ? PhoneSetup26.IOS26 : PhoneSetup26.WATCH) && !installed && !s.running
                && !pending ? View.VISIBLE : View.GONE);
        if (!watch) {
            // which build: only for a new run (a begun or finished one keeps what it was set up with)
            useIos27.setVisibility(!installed && !s.running && !pending ? View.VISIBLE : View.GONE);
        }
    }

    private void update() {
        gpuCard();
        boolean pass = GpuCheck.passed(a);
        boolean failed = !pass && gpuResult != null && gpuResult.ran && !gpuResult.pass;
        SetupService.State s = SetupService.state;
        boolean running = s.running && mine();
        boolean i26done = PhoneSetup26.installed(a, PhoneSetup26.IOS26), wdone = PhoneSetup26.installed(a, PhoneSetup26.WATCH);

        // the GPU line stays quiet once both are there and it had nothing to warn about
        GpuCheck.Result r = gpuResult;
        boolean quiet = pass && r != null && !r.nosparse && r.slow.isEmpty() && GpuCheck.ramWarning(a).isEmpty();
        s26.main.setVisibility(quiet && i26done && wdone ? View.GONE : View.VISIBLE);
        section(s26, primary26, PhoneSetup26.IOS26, pass, failed);
        section(sw, primaryWatch, PhoneSetup26.WATCH, pass, failed);

        // -- This setup
        boolean started = PhoneSetup26.work(a).isDirectory();
        boolean show = pass && (running || started) || mine() && s.error != null;
        run.root.setVisibility(show ? View.VISIBLE : View.GONE);
        // (the hairline above it goes with it)
        root.getChildAt(3).setVisibility(show ? View.VISIBLE : View.GONE);
        run.follow(running);
        long fr = a.getFilesDir().getUsableSpace(), need = PhoneSetup26.needBytes(a);
        String peak = PhoneSetup26.startFree > 0 ? String.format(Locale.ROOT, "; this run's peak so far %s",
                gb(Math.max(0, PhoneSetup26.startFree - PhoneSetup26.lowestFree))) : "";
        SetupSection.say(run.info, String.format(Locale.ROOT, "%s free on this phone%s%s", gb(fr),
                need > 0 && running ? "; the next step needs about " + gb(need) : "", peak), fr < need ? ORANGE : Ui.DIM);
        run.setState(running ? SetupSection.WORKING : SetupSection.OFF,
                running ? (s.text.isEmpty() ? "Working" : s.text) : s.paused ? "Paused" : "Stopped");

        List<IpswFetch.Item> items = running ? s.items : java.util.Collections.emptyList();
        long got = 0, size = 0;
        for (IpswFetch.Item it : items) {
            got += it.done.get();
            size += it.total;
            row(it.member, it.done.get(), it.total, it.state, it.error);
        }
        boolean fetching = running && s.step.endsWith(":fetch");
        if (size > 0 && fetching) {
            total.setText(String.format(Locale.ROOT, "%s of %s from Apple", gb(got), gb(size)));
            totalBar.setProgress((int) (got * 1000 / size));
        } else {
            total.setText(running ? s.text : "Downloads");
            totalBar.setProgress(running && s.pct >= 0 ? s.pct * 10 : 0);
        }
        String more;
        if (fetching && s.mbps > 0 && size > 0) {
            more = String.format(Locale.ROOT, "%.1f MB/s · %s left · %d connections%s", s.mbps, SetupScreen.minutes((size - got) / 1e6 / s.mbps),
                    s.connections, s.servers.isEmpty() ? "" : " · " + s.servers);
        } else if (running && s.waitingForNetwork) {
            more = "No internet connection: it goes on by itself when the phone is online again";
        } else if (running && s.waitingForWifi) {
            more = "Waiting for Wi-Fi (Download on Wi-Fi only is on)";
        } else if (running && s.held) {
            more = s.holdNote != null ? s.holdNote
                    : s.paused ? "Paused" : "Paused: the phone is hot. It goes on by itself when it cools down.";
        } else if (running && s.paused) {
            more = "Paused: what is done stays, Resume goes on from there";
        } else if (running && !PhoneSetup26.charging(a)) {
            more = "Not on the charger: it keeps going, and waits for the charger below 15 %";
        } else {
            more = "";
        }
        SetupSection.say(run.extra, more, Ui.DIM);

        String sig = (pass ? "p" : "") + PhoneSetup26.groups(a) + started;
        if (!sig.equals(steps.getTag())) {
            steps.setTag(sig);
            steps.removeAllViews();
            stepRows.clear();
            for (String g : PhoneSetup26.groups(a)) {
                TextView head = text(steps, PhoneSetup26.SHARED.equals(g) ? "Shared part" : PhoneSetup26.IOS26.equals(g) ? "iOS 26" : "watchOS",
                        13, Ui.FAINT);
                head.setPadding(0, steps.getChildCount() > 1 ? Ui.dp(a, 12) : 0, 0, Ui.dp(a, 4));
                for (String[] st : PhoneSetup26.steps(g)) {
                    TextView t = text(steps, "○  " + st[1], 14.5f, GRAY);
                    t.setPadding(0, Ui.dp(a, 5), 0, Ui.dp(a, 5));
                    stepRows.put(g + ":" + st[0], t);
                }
            }
        }
        for (Map.Entry<String, TextView> e : stepRows.entrySet()) {
            String[] k = e.getKey().split(":");
            String label = "";
            for (String[] st : PhoneSetup26.steps(k[0])) {
                if (st[0].equals(k[1])) {
                    label = st[1];
                }
            }
            TextView t = e.getValue();
            if (PhoneSetup26.done(a, k[0], k[1])) {
                t.setText("✓  " + label);
                t.setTextColor(GREEN);
            } else if (running && e.getKey().equals(s.step)) {
                t.setText("●  " + label + (s.pct >= 0 && !s.step.endsWith(":fetch") ? " (" + s.pct + "%)" : "")
                        + (s.text.isEmpty() || s.step.endsWith(":fetch") ? "" : "\n    " + s.text));
                t.setTextColor(Ui.TEXT);
            } else {
                t.setText("○  " + label);
                t.setTextColor(GRAY);
            }
        }

        errorButtons.setVisibility(mine() && !running && s.error != null && s.record != null ? View.VISIBLE : View.GONE);
        String note = Helper.memoryNote;
        memory.setText(note == null ? "" : note);
        memory.setVisibility(running && note != null ? View.VISIBLE : View.GONE);
        SetupSection.say(run.error, mine() && s.error != null ? s.error : "", RED);
        cancel.setVisibility(pass && (running || started) ? View.VISIBLE : View.GONE);
    }

    private void row(String member, long d, long size, String st0, String err) {
        TextView[] r = fileRows.get(member);
        ProgressBar b = fileBars.get(member);
        if (r == null) {
            r = run.fileRow(files, NAMES.getOrDefault(member, member.substring(member.lastIndexOf('/') + 1)), fileRows.isEmpty());
            b = run.bar(files, 3);
            fileRows.put(member, r);
            fileBars.put(member, b);
        }
        long t = Math.max(1, size);
        String state;
        int color = GRAY;
        switch (st0) {
            case "ok":
                state = "✓ " + gb(size) + " checked";
                color = GREEN;
                break;
            case "verifying":
                state = "Checking…";
                color = Ui.TEXT;
                break;
            case "failed":
                state = "✕ " + (err == null ? "failed" : err);
                color = RED;
                break;
            case "fetching":
                state = gb(d) + " of " + gb(size);
                color = Ui.TEXT;
                break;
            default:
                state = d > 0 ? gb(d) + " of " + gb(size) : gb(size);
                break;
        }
        r[1].setText(state);
        r[1].setTextColor(color);
        b.setProgress((int) (Math.min(d, t) * 1000 / t));
    }
}
