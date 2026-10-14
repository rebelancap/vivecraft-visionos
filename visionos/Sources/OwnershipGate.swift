import Foundation

// Whether this device may run Minecraft. Pure on purpose: the MSA store and
// the launcher call it, and `swiftc -D OWNERSHIP_GATE_TEST` runs the checks
// in this file alone.
//
// Proof of ownership is an account that has already resolved through
// /minecraft/profile (HTTP 200). ownsGame == nil is a legacy resolved account
// from before free accounts existed; those all came back from that 200.
// ownsGame == false is a 404 (no Java Edition). An unresolved account — a
// refresh token that has not been exchanged yet — proves nothing.
//
// Once `stored` is true it stays true. Removing the owning account does not
// re-lock the device. Adding a free account does not clear it. The rule is
// "an owning account has signed in on this device at least once."
enum OwnershipGateLogic {
    struct Account {
        var resolved: Bool
        var ownsGame: Bool?
        /// Resolved and owning. A missing ownsGame counts only because the
        /// account resolved (legacy files); an unresolved account with a
        /// missing ownsGame does not.
        var provesOwnership: Bool { resolved && (ownsGame ?? true) }
    }

    static func unlocked(stored: Bool, accounts: [Account]) -> Bool {
        if stored { return true }
        return accounts.contains { $0.provesOwnership }
    }

    /// nil means every check passed.
    static func selfTest() -> String? {
        let owner = Account(resolved: true, ownsGame: true)
        let legacy = Account(resolved: true, ownsGame: nil)
        let free = Account(resolved: true, ownsGame: false)
        let pending = Account(resolved: false, ownsGame: nil)
        let checks: [(String, Bool, Bool)] = [
            ("empty store stays locked", unlocked(stored: false, accounts: []), false),
            ("unresolved token does not unlock", unlocked(stored: false, accounts: [pending]), false),
            ("free account does not unlock", unlocked(stored: false, accounts: [free]), false),
            ("profile-200 account unlocks", unlocked(stored: false, accounts: [owner]), true),
            ("legacy resolved account unlocks", unlocked(stored: false, accounts: [legacy]), true),
            ("owner among free accounts unlocks", unlocked(stored: false, accounts: [free, pending, owner]), true),
            ("stored flag survives an empty list", unlocked(stored: true, accounts: []), true),
            ("stored flag survives free accounts", unlocked(stored: true, accounts: [free]), true),
            ("stored flag survives a pending token", unlocked(stored: true, accounts: [pending]), true),
        ]
        for (name, got, want) in checks where got != want {
            return "\(name): got \(got), want \(want)"
        }
        return nil
    }
}

#if OWNERSHIP_GATE_TEST
@main
struct OwnershipGateTestMain {
    static func main() {
        if let err = OwnershipGateLogic.selfTest() {
            fputs("FAIL \(err)\n", stderr)
            exit(1)
        }
        print("ok")
    }
}
#endif
