# #deposit-validator, chat export, Tue to Thu

[Tue 10:02] Dana: ok so the no-show deposit. guest books a stay and locks a deposit at the script. if they show up the host gives it back. if they don't the host keeps it. cancel rules like on the website
[Tue 10:03] Sam: one UTxO per booking. datum = guest key hash, host key hash, cancelBy, checkIn, feeBps. skeleton is in Deposit.scala. the types are frozen, backend already serializes them, don't touch
[Tue 10:04] Petra: where's the amount?
[Tue 10:05] Sam: not in the datum. the deposit IS the lovelace sitting in the UTxO, whatever is in there when you spend it. one field less to get wrong
[Tue 10:06] Petra: tokens?
[Tue 10:06] Sam: ADA only. we only ever look at lovelace
[Tue 10:09] Dana: three buttons in the app. guest: "Cancel". host: "Release" (give the deposit back, guest showed up or host is being nice). host: "Claim" (no-show)
[Tue 10:11] Petra: missing datum?
[Tue 10:11] Sam: fail

[Tue 10:15] Sam: time. the script never sees "now", only the validity range of the tx. lower bound L, the ledger gives it inclusive. upper bound U, exclusive. all times are POSIX ms like in the datum. rule of thumb for everything below: if the range doesn't prove it, the answer is no. e.g. a cancel tx with no upper bound = rejected, we can't know it's in time
[Tue 10:17] Dana: free cancellation until cancelBy
[Tue 10:18] Sam: so U <= cancelBy. U equal to cancelBy is still free. one ms more and you're in fee land
[Tue 10:19] Petra: fyi backend sets L = now - 1 min, U = now + 10 min
[Tue 10:20] Sam: fine. for Cancel only U counts. L can be anything, even days old
[Tue 10:24] Dana: after cancelBy the guest can still cancel but pays the fee. until check-in
[Tue 10:26] Ilya: not until check-in. hosts complained, they need notice. no cancel in the last hour
[Tue 10:27] Dana: ok. late cancel until 1h before checkIn
[Tue 10:29] Sam: U <= checkIn - 1h means allowed, with fee. U exactly checkIn - 3_600_000 is ok, one ms more is rejected. after that Cancel is dead for good, the guest shows up or loses it. the 1 hour is a constant, not in the datum

[Tue 11:40] Dana: the fee = feeBps of the deposit. 1000 = 10%
[Tue 11:41] Ilya: rounding?
[Tue 11:41] Dana: host's favour. round up
[Tue 11:43] Sam: ceil(deposit * bps / 10000). 25.000001 ADA at 1000 bps is 2_500_000.1, so 2_500_001 lovelace
[Tue 11:47] Ilya: some hosts set crazy rates, 90%. legal says max 50
[Tue 11:48] Petra: reject the booking then?
[Tue 11:50] Sam: can't, the script doesn't run at lock time. clamp it: anything over 5000 counts as 5000. Cancel must still work with a bad datum
[Tue 11:52] Dana: also a floor. under 2 ADA the host output isn't worth sending. a late cancel always costs at least 2 ADA, even if feeBps is 0
[Tue 11:54] Sam: order then: clamp bps, multiply, round up, then raise to 2 ADA if it's below
[Tue 11:55] Petra: and if the deposit is 1.5 ADA lol
[Tue 11:55] Dana: the app never makes a deposit under 10 ADA
[Tue 11:58] Sam: the app doesn't, but anyone can lock by hand. the guest never loses more than what's locked. if the fee comes out bigger than the deposit, fee = deposit. that's the last step

[Wed 09:12] Sam: late cancel: host must get >= fee
[Wed 09:14] Ilya: no. EXACT. remember the wallet bug last year that sent everything to the host. I want a tx that overpays the host to fail
[Wed 09:16] Sam: ok. exactly the fee. sum of lovelace over all outputs to the host's payment key hash == fee. staking part of the address ignored. not one lovelace more, not one less
[Wed 09:17] Petra: and the guest's part?
[Wed 09:18] Sam: not checked. the guest signs the cancel, where the rest goes is their business. change, another wallet, whatever
[Wed 09:19] Petra: and on a free cancel?
[Wed 09:19] Sam: no output checks at all. guest signature + time, that's it
[Wed 09:21] Petra: who signs Cancel, can the host cancel for the guest?
[Wed 09:21] Dana: guest only. host has Release for that

[Wed 09:30] Dana: Release: host signs, guest gets the deposit back
[Wed 09:32] Sam: outputs to the guest (sum, by payment key hash, same way as above) >= deposit. at least, not exact. the guest can get more, the host may add a refund for something else
[Wed 09:33] Petra: time window?
[Wed 09:34] Dana: none. the host can always give the money back. before cancelBy, during the stay, a year later. no clock on Release
[Wed 09:36] Petra: can the guest release to themselves?
[Wed 09:36] Sam: no!! host signature only. otherwise the deposit is pointless

[Wed 14:05] Dana: Claim: no-show. host signs and takes the deposit
[Wed 14:06] Ilya: guests arrive late all the time. grace period, 4h
[Wed 14:09] Dana: 6h. final
[Wed 14:12] Sam: hardcoded 6h, not in the datum. Claim when L > checkIn + 6h. strictly greater: the guest has the full 6 hours, at exactly checkIn + 6h the host still can't. U doesn't matter for Claim
[Wed 14:13] Petra: outputs?
[Wed 14:14] Sam: none checked. the host signs, the host decides where it goes

[Thu 08:50] Sam: one more thing, double satisfaction. a host with two bookings from the same guest could release both with one 100 ADA payment and pocket the other deposit. one payment must never settle two deposits
[Thu 08:52] Sam: simplest fix and I'm doing that: exactly one input from the deposit script per tx, whatever the action. two deposit inputs in one tx = fail
[Thu 08:53] Petra: so no batching
[Thu 08:53] Sam: no batching. v1

[Thu 09:10] Petra: what if cancelBy in the datum is later than checkIn - 1h?
[Thu 09:11] Sam: the free rule is checked first. U <= cancelBy is free, period. the app shouldn't produce that datum though
[Thu 09:15] Ilya: partial release? keep 20 ADA for the broken lamp
[Thu 09:15] Dana: not in v1
[Thu 09:20] Petra: host == guest, for test bookings?
[Thu 09:20] Dana: don't care, no special case
[Thu 09:31] Ilya: do we need an admin key or a pause switch?
[Thu 09:33] Dana: undecided, I'll ask legal. nothing like that in v1 unless they say so
[Thu 09:40] Petra: can the script make sure a new booking is locked correctly, e.g. cancelBy < checkIn?
[Thu 09:41] Sam: no. nothing runs at lock time. the app builds the lock tx and we trust it for that
