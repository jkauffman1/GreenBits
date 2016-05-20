package com.greenaddress.greenbits.wallets;

import android.support.annotation.NonNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.greenaddress.greenapi.ISigningWallet;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenapi.PreparedTransaction;
import com.satoshilabs.trezor.Trezor;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.spongycastle.util.encoders.Hex;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

public class TrezorHWWallet implements ISigningWallet {

    private static final ListeningExecutorService es = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    private final Trezor trezor;
    private final List<Integer> addrn;

    public TrezorHWWallet(final Trezor t) {
        trezor = t;
        addrn = new LinkedList<>();
    }

    private TrezorHWWallet(final TrezorHWWallet parent, final Integer childNumber) {
        trezor = parent.trezor;
        addrn = new LinkedList<>(parent.addrn);
        addrn.add(childNumber);
    }

    @NonNull
    @Override
    public ISigningWallet deriveChildKey(@NonNull final ChildNumber childNumber) {
        return new TrezorHWWallet(this, childNumber.getI());
    }

    @Override
    public byte[] getIdentifier() {
        return getPubKey().toAddress(Network.NETWORK).getHash160();
    }

    @Override
    public boolean canSignHashes() {
        return false;
    }

    @NonNull
    @Override
    public ListenableFuture<ECKey.ECDSASignature> signHash(final byte[] hash) {
        return Futures.immediateFuture(null);
    }

    @NonNull
    @Override
    public ListenableFuture<ECKey.ECDSASignature> signMessage(final String message) {
        return es.submit(new Callable<ECKey.ECDSASignature>() {
            @Override
            public ECKey.ECDSASignature call() throws Exception {
                final Integer[] intArray = new Integer[addrn.size()];
                return trezor.MessageSignMessage(addrn.toArray(intArray), message);
            }
        });
    }

    @Override
    public DeterministicKey getPubKey() {
        final Integer[] intArray = new Integer[addrn.size()];
        final String[] xpub = trezor.MessageGetPublicKey(addrn.toArray(intArray)).split("%", -1);
        final String pkHex = xpub[xpub.length - 2];
        final String chainCodeHex = xpub[xpub.length - 4];
        final ECKey pubKey = ECKey.fromPublicOnly(Hex.decode(pkHex));
        return new DeterministicKey(new ImmutableList.Builder<ChildNumber>().build(),
                                    Hex.decode(chainCodeHex), pubKey.getPubKeyPoint(), null, null);
    }

    @NonNull
    @Override
    public List<ECKey.ECDSASignature> signTransaction(final PreparedTransaction tx, final byte[] gait_path) {
        final boolean isMainnet = Network.NETWORK.getId().equals(MainNetParams.ID_MAINNET);
        return trezor.MessageSignTx(tx, isMainnet ? "Bitcoin": "Testnet", gait_path);
    }

    @Override
    public boolean requiresPrevoutRawTxs() {
        return true;
    }
}
