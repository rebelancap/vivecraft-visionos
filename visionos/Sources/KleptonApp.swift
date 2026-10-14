import SwiftUI
import Foundation
import CompositorServices

// The visionOS host app (Swift for the platform layer).
//
// The boot gate is deliberately a plain WindowGroup and nothing else: the gate is "the
// guest boots inside an app bundle under AMFI, and a veneer executes", and an
// ImmersiveSpace would have put Compositor Services into the picture before
// there was anything to present, so a failure in either half would have read
// as a failure of the other.
//
// The immersive path sits BESIDE it rather than in place of it, behind
// KL_IMMERSIVE. The window still boots and still reports, so the boot measurement
// stays takeable on the same binary — which matters because that measurement
// is how a device regression gets localised.

/// A KL_* knob read by value, not by presence — the Swift twin of
/// `kl_env_on()`. Both defaults below are ON, so `KL_FOO=0` has to be a real
/// way to say no rather than a second way to say yes.
func klEnvOn(_ name: String, default dflt: Bool) -> Bool {
    guard let v = ProcessInfo.processInfo.environment[name] else { return dflt }
    return !["", "0", "no", "off", "false"].contains(v.lowercased())
}

/// The same for a scalar — `kl_env_float()`'s twin. An unparseable value falls
/// back to the default rather than to zero: a typo in a gain knob that silently
/// means "off" is the kind of thing that gets diagnosed as broken hardware.
func klEnvFloat(_ name: String, _ dflt: Float) -> Float {
    guard let v = ProcessInfo.processInfo.environment[name], let f = Float(v) else { return dflt }
    return f
}

enum Immersive {
    static let id = "KleptonImmersive"
    // Default ON for Beat Saber, OFF for Steam Link, and the difference is not a
    // preference — it is what each app can currently put on screen.
    //
    // Klepton runs a VR title, so the immersive space IS the app: launching from
    // the Home View and getting a window with a Boot button is a test harness,
    // not a product. The window-and-report shape stays exactly one knob away
    // (KL_IMMERSIVE=0) because it is the measurement that localises a device
    // regression.
    //
    // Steam Link is the other way round until its 2D shell has a window. Its VR
    // half cannot draw anything without an authorized session, and a session
    // arrives by hand (KL_SLINK_SARGS) — so the default launch opens an
    // immersive space that is black by construction and hides the one surface
    // with information on it. KL_IMMERSIVE=1 turns it on for a run that HAS a
    // session, which is the run that wants it.
    //
    // Reads kl_app_target_is_steamlink(), so it is only meaningful after
    // kl_app_configure. Both call sites are inside boot(), after configure has
    // returned; the scene body reads `mixed`, not this.
    static var wanted: Bool {
        // The native guest (Vivecraft) is VR by default since M2: kl_app.c's
        // native_vr() reads the same knob with the same default, so the game's
        // mode (vrEnabled, -Dklepton.vr.android) and this scene always agree.
        klEnvOn("KL_IMMERSIVE", default: kl_app_target_is_steamlink() == 0)
    }

    // .mixed or .full, decided per target — and this is the setting the room
    // breaks through.
    //
    // .mixed was the right default while the picture was being *found*: the guest
    // renders an opaque world, so passthrough shows through nowhere it matters,
    // and seeing the room makes scale, distance and IPD judgeable against real
    // surroundings instead of against a black void.
    //
    // It is the wrong default for a title someone is PLAYING. In .mixed the
    // system is compositing the room with our content every frame, and it decides
    // per pixel and per moment what wins — so a person who walks in, a monitor, a
    // desk edge flicker into the scene. That is not visionOS's close-range people
    // awareness (which is a safety feature and cannot be turned off, see
    // superhot/STATUS.md); it is us asking for the room and then being surprised
    // to get it. Phase 11: Austin played SUPERHOT worn and reported exactly this.
    //
    // So the default is now the target's own answer — `opaque_world` in kl_app.c's
    // TARGETS table, on for superhot and off for the other two, which leaves Beat
    // Saber and Steam Link behaving precisely as they did. KL_FULL is unchanged
    // and still decides it outright in either direction: KL_FULL=1 forces .full,
    // KL_FULL=0 forces .mixed, and unset means "whatever this title wants".
    static var mixed: Bool {
        !klEnvOn("KL_FULL", default: kl_app_target_opaque_world() != 0)
    }

    /// Whether the wearer's real arms and hands are drawn over the immersive
    /// scene.
    ///
    /// visionOS shows them by default, which is right for an app the wearer
    /// reaches into and wrong for a VR title that models the hands itself:
    /// SUPERHOT draws low-poly hands, puts guns in them and throws them, and the
    /// real pair arrives in the same place, tracked slightly differently, doing
    /// something slightly different. Two left hands, disagreeing.
    ///
    /// `.hidden` for a target whose guest draws its own (`own_hands` in kl_app.c),
    /// `.automatic` otherwise — so Beat Saber's behaviour is unchanged by this
    /// commit, and a BS player who wants it can flip it the same way. `KL_HANDS`
    /// overrides in both directions: `KL_HANDS=1` puts the real limbs back
    /// (useful for judging whether the guest's hands are tracking the same
    /// pose), `KL_HANDS=0` takes them away.
    static var upperLimbs: Visibility {
        klEnvOn("KL_HANDS", default: kl_app_target_own_hands() == 0)
            ? .automatic : .hidden
    }

    // How many times the CompositorLayer closure has been entered. SwiftUI may
    // re-evaluate a scene, and a second layer would mean the render loop the log
    // describes is not the one on screen.
    nonisolated(unsafe) private static var closures = 0
    static func bump() -> Int { closures += 1; return closures }

    /// Whether the system's persistent overlays — the Home indicator, and the
    /// hand-gesture affordance that fades in beneath it — stay on top of the
    /// guest's picture.
    ///
    /// Hidden by default. The overlay is drawn by the system *over* the
    /// immersive scene, and both guests here put their own interactive content
    /// exactly where it lands: Beat Saber's lower menu row and Steam Link's
    /// dashboard toolbar are both near the bottom of the field, so the overlay
    /// sits on the controls rather than beside them. It also reappears on every
    /// hand raise, which for a title driven entirely by raised hands is
    /// continuous.
    ///
    /// `.hidden` is a request, not a guarantee — the system still shows the
    /// indicator at moments it considers mandatory (the first seconds of a
    /// space, a pending system alert), which is why this is the same `Visibility`
    /// ALVR passes rather than a claim that it is gone. `KL_OVERLAYS=1` puts it
    /// back, which is what a run wants when the question is whether the system
    /// still thinks our space is on screen at all.
    static var systemOverlays: Visibility {
        klEnvOn("KL_OVERLAYS", default: false) ? .automatic : .hidden
    }
}

/// What backgrounding means for this process: the end of it.
///
/// ALVR's shape, and for the same reason. Everything the guest is holding when
/// the app goes away is either unresumable or expensive to re-establish — the
/// ARKit session, the Compositor Services layer, the ANGLE context and the eye
/// swapchain behind it, FMOD's OpenSL player, and a Unity engine that has been
/// told it is on a Quest and never expects the display to leave. Resuming that
/// correctly is a project of its own; resuming it *incorrectly* is a class of
/// bug that reports itself as "the second run is broken" long after the cause.
/// Exiting makes every launch the first launch.
///
/// `KL_EXIT_ON_BACKGROUND=0` keeps the old behaviour, which is what a debugging
/// session wants when the headset comes off with a capture still open.
enum Lifecycle {
    static func scenePhaseChanged(to phase: ScenePhase) {
        NSLog("[app] scene phase -> \(phase)")
        // The crown press delivers .inactive at once but .background only when
        // the system gets round to it (measured ~30 s). The music belongs to
        // the immersive moment: go silent on the first, and still exit on the
        // second. .active restores — the boot transition passes through a
        // 2 s .inactive on the way in, which this round-trips harmlessly.
        switch phase {
        case .inactive: KleptonAudio.setSilenced(true)
        case .active:   KleptonAudio.setSilenced(false)
        default: break
        }
        // Coming back is the audio's cue, and it needs one: this OS silently
        // stops calling CoreAudio's render callback across a scene transition —
        // the boot window being closed while the immersive space runs is one,
        // a Digital Crown press to passthrough is another — with no error and
        // no interruption notification. See kl_audio_resume; the compositor
        // hooks the immersive half of the same transition, and kl_audio's
        // heartbeat catches whatever neither of them sees.
        //
        // Unconditional rather than "only if we were away". A rebuild of a
        // healthy unit costs a few milliseconds of silence and cannot go wrong;
        // the state that would let us skip it is precisely the state this
        // platform lies about.
        if phase == .active { kl_audio_resume() }
        // And the controllers need one for the same reason the audio does.
        // GameController stops forwarding events to an app that is not
        // frontmost, and a glanced-at visionOS notification takes the
        // foreground away without ever backgrounding us — so `.active` is the
        // only signal this process gets that input may need re-establishing.
        // See KleptonControllers.appBecameActive.
        if phase == .active { KleptonControllers.appBecameActive() }
        guard phase == .background else { return }
        // The pipeline cache save is throttled (kl_vulkan.c); write its tail
        // now, while the process is certainly still alive to do it.
        kl_vulkan_pipeline_cache_flush()
        guard klEnvOn("KL_EXIT_ON_BACKGROUND", default: true) else {
            NSLog("[app] backgrounded; KL_EXIT_ON_BACKGROUND=0, staying alive")
            return
        }
        NSLog("[app] backgrounded — exiting (KL_EXIT_ON_BACKGROUND)")
        // The last word on whether the run made a sound. This is the ONLY exit a
        // headset run ever takes — the report at the end of kl_app_lifecycle
        // belongs to the synthetic sweep and has never run on device — so if the
        // audio numbers are not printed here they are not printed at all.
        kl_audio_heartbeat(stderr)
        // Flush before exit rather than relying on it. The guest's log is a
        // file in the container (kl_app.c redirects stdout/stderr there) and it
        // is the only account of the run that survives; `exit` does flush
        // stdio, but it also runs atexit handlers and static destructors inside
        // a guest whose threads are still live, and one of those blocking would
        // turn a clean exit into a watchdog kill with a truncated log.
        fflush(nil)
        exit(0)
    }
}

@main
struct KleptonApp: App {
    init() {
        // Before any view reads launch.txt. A bundled payload with an empty
        // container has to be a game dir by the time the launcher appears.
        KleptonPayload.materialize()
    }
    @Environment(\.scenePhase) private var scenePhase
    /// Observed so the Matting panel's hand-matting toggle re-evaluates the
    /// immersive scene's `.upperLimbVisibility` live. The object is the shared
    /// singleton the panel edits, not a second copy.
    @ObservedObject private var chroma = KleptonChroma.shared

    var body: some Scene {
        WindowGroup { BootView() }
            .defaultSize(width: 1280, height: 800)
            // On the app's phase, not this window's: closing the boot window
            // while the immersive space is up is not backgrounding, and must
            // not be treated as it. The log line above every decision is what
            // makes that distinction checkable on a device rather than assumed.
            .onChange(of: scenePhase) { _, phase in Lifecycle.scenePhaseChanged(to: phase) }

        ImmersiveSpace(id: Immersive.id) {
            CompositorLayer(configuration: KleptonStageConfiguration()) { layerRenderer in
                // How many times this closure runs, and for which renderer. If
                // it runs twice, the loop we watch in the log is not necessarily
                // the layer being displayed — which would explain a correct pass
                // that nobody sees.
                NSLog("[cp] CompositorLayer closure #\(Immersive.bump()) "
                      + "renderer=\(ObjectIdentifier(layerRenderer))")
                KleptonCompositor(layerRenderer).startRenderLoop()
            }
        }
        // Per target now — see Immersive.mixed. Superhot asks for .full so the
        // room cannot composite into a game being played; Beat Saber and Steam
        // Link keep .mixed. KL_FULL decides it outright either way.
        .immersionStyle(selection: .constant(Immersive.mixed ? .mixed : .full),
                        in: .mixed, .full)
        // The wearer's real arms — see Immersive.upperLimbs. Hidden for a guest
        // that draws its own hands, which is superhot and only superhot today.
        // On the Scene, like persistentSystemOverlays below and for the same
        // reason: a CompositorLayer has no view hierarchy to attach a View-level
        // modifier to.
        .upperLimbVisibility(Immersive.upperLimbs)
        // See Immersive.systemOverlays. On the scene, not on a view inside it:
        // a CompositorLayer has no view hierarchy for the View-level modifier to
        // attach to, so the Scene-level one is the only one that applies here.
        .persistentSystemOverlays(Immersive.systemOverlays)
        // Whether the system mattes the user's own hands and arms over the
        // guest's picture. Read from the observed settings rather than from the
        // environment directly, so the toggle in the Matting panel takes effect
        // without a relaunch — which is the whole reason these live in a window
        // that stays open beside the space.
        .upperLimbVisibility(chroma.upperLimbVisibility)
    }
}

/// Where the runtime's two roots live.
///
/// Guest code stays in the bundle. A vivecraft build with KL_BUNDLE_PAYLOAD
/// also carries the read-only game payload (jars, assets) in klepton-payload/
/// and symlinks it from Documents on first launch (KleptonPayload.swift).
/// Other targets still stage assets into the container: AMFI accepts a dylib
/// inside a bundle we signed, and the big asset trees carry no code.
enum Paths {
    static var resources: String { Bundle.main.bundlePath }
    static var container: String {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].path
    }
}

struct BootView: View {
    @State private var log = ""
    @State private var status = "idle"
    @State private var running = false
    @State private var finished = false
    @State private var succeeded = false
    @State private var openedSpace = false
    // The flat guest's picture, and the handoff that ends it. Polled rather
    // than pushed: both are C state written by a guest thread, and a callback
    // out of one into SwiftUI would be a main-actor hop from a thread that is
    // mid-frame. A timer at 5 Hz costs nothing and cannot deadlock.
    @State private var showShell = false
    @State private var handedOff = false
    // The Minecraft row's themed launcher (KleptonMCLauncher.swift) stands in
    // front of the report; "Details" flips to the report and back.
    @ObservedObject private var launcher = MCLauncherModel.shared
    @ObservedObject private var accounts = MSAAccounts.shared
    @Environment(\.openImmersiveSpace) private var openImmersiveSpace
    @Environment(\.dismissImmersiveSpace) private var dismissImmersiveSpace
    // Quit to the launcher (native guest): how many of the guest's returns this
    // window has already answered (kl_app_native_runs counts them in C).
    @State private var gamesAnswered: Int32 = 0

    var body: some View {
        Group {
            if showShell { ShellWindow() }
            else if MCLauncher.enabled, !launcher.details {
                MCLauncherView(running: running, finished: finished, succeeded: succeeded, status: status,
                               onLaunch: { launch() }, onDetails: { launcher.details = true })
            } else { bootReport }
        }
        .task { await watchPresentation() }
        .task { await autobootIfAsked() }
    }

    /// Boots on its own. Tapping an app and then tapping Boot is a harness,
    /// not a product — and the scripted paths (`visionos/run.sh`) wanted
    /// this anyway, which is what KL_AUTOBOOT was for. `KL_AUTOBOOT=0`
    /// restores the button-only shape for hand-driven debugging, where the
    /// point is to attach or start a capture before the guest runs.
    /// The Minecraft launcher is the exception: it IS the product's front
    /// door, so it waits for Launch unless KL_AUTOBOOT=1 is set explicitly
    /// (run.sh sets it), and then boots with the dev run's own KL_MC_ARGS.
    private func autobootIfAsked() async {
        // The gate reads the store, so the list (and deviceUnlocked) has to be
        // loaded before either the button or an autoboot decides. KL_MSA=0
        // still reloads: that knob skips the session, not the gate.
        if MSAAccounts.isMinecraft || MSAAccounts.wanted {
            await MSAAccounts.shared.reload()
        }
        if MCLauncher.enabled {
            guard MCLauncher.autoboot else { return }
            guard OwnershipGate.allowsLaunch() else {
                OwnershipGate.recordRefusal()
                return
            }
            if MSAAccounts.wanted, !(await MSAAccounts.shared.countdownThenBoot()) { return }
            MCLauncherModel.shared.prepareForLaunch(applyPlay: false)
            boot()
            return
        }
        guard klEnvOn("KL_AUTOBOOT", default: true) else { return }
        guard OwnershipGate.allowsLaunch() else {
            OwnershipGate.recordRefusal()
            return
        }
        // A few seconds to pick another account (KL_MSA_CHOOSER_SECONDS);
        // touching the panel holds the boot for the Boot button.
        if MSAAccounts.wanted, !(await MSAAccounts.shared.countdownThenBoot()) { return }
        if MSAAccounts.isMinecraft { MCProfiles.prepareForLaunch() }
        boot()
    }

    /// A person pressed Launch/Boot: the launcher's choices first.
    private func launch() {
        guard OwnershipGate.allowsLaunch() else {
            OwnershipGate.recordRefusal()
            return
        }
        if MCLauncher.enabled { MCLauncherModel.shared.prepareForLaunch(applyPlay: true) }
        else if MSAAccounts.isMinecraft { MCProfiles.prepareForLaunch() }   // KL_LAUNCHER=0: same game dirs
        boot()
    }

    /// Which of the two things the window can be showing, decided by what the
    /// guest is actually producing rather than by a knob.
    ///
    /// kl_present observes the mode — a window surface was created, or an eye
    /// texture was set up — so a guest that surprises us is described correctly
    /// rather than according to a flag someone remembered to set. The 2D->VR
    /// handoff is the transition kl_present.h was written for.
    private func watchPresentation() async {
        while !Task.isCancelled {
            let mono = kl_present_mode_now() == KL_PRESENT_MONO
            if mono != showShell { showShell = mono }

            if kl_app_vrlink_pending() != 0, !handedOff {
                handedOff = true
                NSLog("[app] 2D -> VR handoff: \(String(cString: kl_app_vrlink_sargs()))")
                // Unconditionally, not behind Immersive.wanted: that default
                // says what a LAUNCH should open, and this is a run that has
                // just been handed the one thing the VR half cannot start
                // without. openedSpace is shared with boot() so the two paths
                // cannot both open it.
                if !openedSpace {
                    openedSpace = true
                    let r = await openImmersiveSpace(id: Immersive.id)
                    NSLog("[cp] openImmersiveSpace (after handoff) -> \(r)")
                }
            }
            // The game closed its window (Quit Game -> glfwTerminate): its picture
            // is gone, so the space goes NOW rather than when main returns (which
            // launch 17 showed can take until the 15 s watchdog). The launcher
            // reads Quitting… until the run ends (kl_app.c's KL_MC_QUIT_GRACE_MS).
            if kl_app_target_is_native() != 0, !launcher.quitting, kl_app_native_closing() != 0 {
                launcher.quitting = true
                NSLog("[launcher] the game closed its window — dismissing the space, waiting for the run to end")
                fputs("[launcher] the game closed its window — dismissing the space\n", stderr)
                if openedSpace {
                    await dismissImmersiveSpace()
                    openedSpace = false
                }
            }
            if kl_app_target_is_native() != 0, kl_app_native_runs() > gamesAnswered {
                gamesAnswered = kl_app_native_runs()
                await gameEnded()
            }
            try? await Task.sleep(for: .milliseconds(200))
        }
    }

    /// The native guest (Minecraft) returned from its main: Quit Game. Before
    /// this the immersive space stayed up showing the last picture reprojected
    /// (the "black environment") and the launcher still said Playing. Now the
    /// space goes away and the launcher is back to idle — accounts, play target
    /// and resolution editable — and its Launch runs the game again in this
    /// process (kl_app_native_rerun). If the run's isolate could not be torn
    /// down (kl_app_native_rerun_ok, see native_isolate_teardown) a second run
    /// cannot share the process: the app exits instead, so the next tap on
    /// the icon lands on the launcher rather than on a black space.
    private func gameEnded() async {
        let rc = kl_app_native_rc()
        let rerun = kl_app_native_rerun_ok() != 0
        if openedSpace {
            await dismissImmersiveSpace()
            openedSpace = false
        }
        if !rerun {
            NSLog("[launcher] game ended (rc=\(rc)) — no re-run in this process; exiting so the next launch starts at the launcher")
            fputs("[launcher] game ended (rc=\(rc)) — no re-run in this process; exiting\n", stderr)
            fflush(nil)
            exit(0)
        }
        NSLog("[launcher] game ended (rc=\(rc)) — back to the launcher")
        fputs("[launcher] game ended (rc=\(rc)) — back to the launcher\n", stderr)
        fflush(nil)
        status = "game ended (rc=\(rc))"
        running = false; finished = false; succeeded = false
        launcher.quitting = false
        launcher.details = false
        launcher.gameEnded()
    }

    private var bootReport: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                if MCLauncher.enabled {
                    Button("Launcher", systemImage: "chevron.backward") { launcher.details = false }
                }
                Text("Klepton").font(.largeTitle.bold())
                // The guest, by name. Two apps are built from this tree and they
                // look identical from the front; a boot log that does not say
                // which one produced it is a log that can be read as the other's.
                Text(String(cString: kl_app_target_name()))
                    .foregroundStyle(.secondary)
                Spacer()
                if finished {
                    Label(succeeded ? "initJni completed" : status,
                          systemImage: succeeded ? "checkmark.circle.fill" : "xmark.octagon.fill")
                        .foregroundStyle(succeeded ? .green : .red)
                }
            }

            ScrollView {
                Text(log.isEmpty ? "Press Boot to load the guest chain." : log)
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: .infinity)

            // The controller-alignment sliders, in THIS window because it stays
            // open beside the immersive space — so the guest keeps rendering
            // while they move, which is the entire point of them. Collapsed by
            // default: it is a tuning instrument, not part of booting.
            DisclosureGroup("Controller alignment") { TuningView() }
                .font(.callout)

            // ...and the matting dials, the same way and in the same window.
            // Collapsed by default for the same reason: a key colour is found
            // once for a room and then left alone.
            DisclosureGroup("Matting") { ChromaView() }
                .font(.callout)

            // The Minecraft row's accounts (KleptonMSA.swift): which one this
            // boot signs in as, Add account…, default. Locked once booted.
            // Shown while the ownership gate is shut even if KL_MSA=0, so
            // Add account is how the device gets unlocked.
            if MSAAccounts.wanted || (OwnershipGate.enforced && accounts.gateReady && !accounts.deviceUnlocked) {
                AccountsPanel(locked: running || finished)
            }
            if OwnershipGate.enforced && accounts.gateReady && !accounts.deviceUnlocked {
                Text(OwnershipGate.sentence)
                    .font(.body)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 16) {
                Button(running ? "Running…" : "Boot") { launch() }
                    .disabled(running || OwnershipGate.blocksLaunch(ready: accounts.gateReady, unlocked: accounts.deviceUnlocked))
                if running { ProgressView() }
                if finished, !log.isEmpty {
                    ShareLink(item: log) { Label("Export log", systemImage: "square.and.arrow.up") }
                }
                Spacer()
                Text(status).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(24)
    }

    private func boot() {
        // Guarded on this side too, not only in kl_app.c. The guest boots
        // once per process — the runtime's JNI tables are process-global, so a
        // second run re-registers every native onto the same table and the
        // numbers stop being comparable to the host's. Disabling the button
        // while running is not enough: the run *finishes*, and the obvious
        // next thing to do with a finished run is press Boot again.
        guard !running, !finished else { return }
        // Before the image is mapped and before the immersive space opens.
        // kl_app.c checks again at native_run, which is the call every path
        // (this one, a re-run, KL_LAUNCHER=0, a script) makes before main.
        guard OwnershipGate.allowsLaunch() else {
            OwnershipGate.recordRefusal()
            return
        }
        // Launch after a Quit Game (the native guest's quit-to-launcher): the
        // process already booted, so this is a re-run, not a boot — the env
        // file and the command line are rebuilt and the space opens again.
        if kl_app_native_runs() > 0 {
            rerun()
            return
        }
        running = true; log = ""

        // Off the main thread: the guest blocks — Unity's Baselib waits on
        // futexes, IL2CPP's GC suspends the world — and a blocked main thread
        // is a watchdog kill on this platform, which would present as a crash
        // with no report rather than as the hang it is.
        Thread.detachNewThread {
            let rc = kl_app_configure(Paths.resources, Paths.container)
            if rc != 0 {
                // To the SYSTEM log as well as the window, because this is the
                // one failure that happens before there is a klepton-boot.log to
                // write into — kl_app_boot opens that file, and configure runs
                // first. A scripted run therefore saw no output at all and read
                // as a launch that never reached our code; the reason was
                // sitting in a SwiftUI label nobody was looking at.
                NSLog("[app] configure failed: \(String(cString: kl_app_status()))")
                DispatchQueue.main.async {
                    status = String(cString: kl_app_status())
                    log = "configure failed: \(status)\n\n" + stagingHelp
                    running = false; finished = true; succeeded = false
                }
                return
            }

            let logPath = String(cString: kl_app_log_path())
            // The report is read back from the file rather than returned,
            // because an unimplemented JNI call aborts the process by design.
            // If this run dies, the next launch still shows how far it got.
            let poll = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
                if let t = try? String(contentsOfFile: logPath, encoding: .utf8) {
                    DispatchQueue.main.async { log = t }
                }
            }
            RunLoop.current.add(poll, forMode: .common)

            var result = kl_app_boot()

            // The audio session, and the placement is deliberate on both sides.
            //
            // AFTER kl_app_boot, because boot is what opens (and truncates)
            // Documents/klepton-boot.log — anything this prints earlier goes to
            // a stderr nobody can retrieve from a headset. The first device run
            // did exactly that: the session was configured correctly and the two
            // lines saying so were invisible, which is the wrong way round for
            // the one subsystem whose failure mode is silence.
            //
            // BEFORE the lifecycle, because that is when FMOD opens its OpenSL
            // player, and kl_audio builds its output unit against the session's
            // measured sample rate. A unit initialised against a guess starts
            // successfully and produces nothing, with no error code anywhere in
            // the path. There is a wide margin here: boot ends at initJni and
            // the first [sl] line is thousands of log lines later.
            KleptonAudio.start()
            // Carry on into the Android lifecycle when asked, in the same
            // process and on the same thread. Only after boot has reported, so a
            // lifecycle failure cannot be mistaken for a boot failure — and only
            // when asked, because the gate is a boot that stops at initJni and
            // it must stay possible to take exactly that measurement.
            //
            // This is where libil2cpp (66 MB, 3,083 x18 veneers) first loads
            // under AMFI, and where the synthetic /proc is first read on device.
            //
            // Skipped under KL_IMMERSIVE: there the compositor is the frame
            // clock and drives kl_app_lifecycle_begin/_frame itself, and both
            // entries are once-per-process, so running the pump here as well
            // would take the lifecycle's only turn.
            //
            // Which knob asks for it depends on the guest, because a frame
            // budget is meaningless to one that owns its own frame loop. Beat
            // Saber's is KL_FRAMES — nativeRender calls, counted. Steam Link's
            // is KL_SLINK_WAIT — seconds of looper pump, exactly as it is on the
            // command line. Gating both on KL_FRAMES was the first version and
            // it made the window path on that target load the chain, report, and
            // then never call ANativeActivity_onCreate at all: a run that looks
            // finished and never started the activity.
            // ...and whether it is asked for AT ALL differs too. Beat Saber's
            // window path stops at initJni unless KL_FRAMES says otherwise,
            // because continuing is what the gate must not do. Steam Link's has
            // no such reason: its chain report is printed and complete BEFORE
            // the activity starts, so that measurement stays takeable from any
            // run, and stopping there instead would mean the default launch
            // loads the guest and then does nothing at all. So it always runs,
            // bounded by KL_SLINK_WAIT (30 s by default) — bounded rather than
            // open-ended because the media/audio/XR/GL report is written at the
            // END of the pump, and that is the report a working run has no other
            // way of producing.
            //
            // The Unreal guest is the Steam Link arm's shape for the Steam Link
            // arm's reasons: its budget is KL_UE4_WAIT in seconds (a frame count
            // means nothing to a guest that owns its frame loop), and its report
            // is written at the END of the pump, so stopping at the chain would
            // mean the default launch loads the guest and then does nothing.
            let env = ProcessInfo.processInfo.environment
            let ownsLoop = kl_app_target_owns_frame_loop() != 0
            if result == 0, !Immersive.wanted,
               ownsLoop || env["KL_FRAMES"] != nil {
                result = kl_app_lifecycle(UInt32(env["KL_FRAMES"] ?? "") ?? 1)
            }
            poll.invalidate()

            let text = (try? String(contentsOfFile: logPath, encoding: .utf8)) ?? ""
            DispatchQueue.main.async {
                log = text
                status = String(cString: kl_app_status())
                succeeded = (result == 0)
                running = false; finished = true
                // The space opens only after boot has succeeded, and from here
                // rather than from .task, because the compositor's very first
                // act is kl_app_lifecycle_begin() — which needs the UnityPlayer
                // that kl_app_boot creates. Opening it in parallel with boot
                // would be a race whose losing side reports "kl_app_boot must
                // run first" and reads like a missing binding.
                if result == 0, Immersive.wanted, !openedSpace {
                    openedSpace = true
                    // The result is logged rather than discarded: `.error` and
                    // `.userCancelled` both leave the app alive with no
                    // compositor, which is indistinguishable in the log from a
                    // compositor that came up and died — and those want
                    // completely different next moves.
                    Task {
                        let r = await openImmersiveSpace(id: Immersive.id)
                        NSLog("[cp] openImmersiveSpace -> \(r)")
                    }
                }
            }
        }
    }

    private func rerun() {
        running = true
        Thread.detachNewThread {
            // Off the main thread: it reads files and asks for the MSA session
            // (a network round trip in kl_msa_session).
            let rc = kl_app_native_rerun()
            DispatchQueue.main.async {
                status = String(cString: kl_app_status())
                if rc != 0 {
                    // Not finished=true: that would lock the launcher with no way
                    // to try again (e.g. Launch pressed before the old layer's
                    // render loop had ended). Idle, with the reason in the footer.
                    NSLog("[launcher] re-run refused: \(status)")
                    running = false; finished = false; succeeded = false
                    return
                }
                running = false; finished = true; succeeded = true
                // The render loop's end silenced the session; this run needs it.
                KleptonAudio.setSilenced(false)
                if Immersive.wanted, !openedSpace {
                    openedSpace = true
                    Task {
                        let r = await openImmersiveSpace(id: Immersive.id)
                        NSLog("[cp] openImmersiveSpace (re-run) -> \(r)")
                    }
                }
            }
        }
    }

    private var stagingHelp: String {
        """
        The APK assets are staged into the app's Documents container rather \
        than bundled, so they survive a reinstall and are uploaded once. Run:

            KLEPTON_TARGET=\(String(cString: kl_app_target_name())) \
                visionos/stage_assets.sh <device-udid>

        from the repo root with the device paired and unlocked.
        """
    }
}
