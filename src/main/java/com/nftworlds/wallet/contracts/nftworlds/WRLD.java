package com.nftworlds.wallet.contracts.nftworlds;

import com.nftworlds.wallet.NFTWorlds;
import com.nftworlds.wallet.config.Config;
import com.nftworlds.wallet.contracts.wrappers.ethereum.EthereumWRLDToken;
import com.nftworlds.wallet.contracts.wrappers.polygon.PolygonWRLDToken;
import com.nftworlds.wallet.event.PeerToPeerPayEvent;
import com.nftworlds.wallet.event.PlayerTransactEvent;
import com.nftworlds.wallet.objects.NFTPlayer;
import com.nftworlds.wallet.objects.Network;
import com.nftworlds.wallet.objects.TransactionObjects;
import com.nftworlds.wallet.objects.Wallet;
import com.nftworlds.wallet.objects.payments.PaymentRequest;
import com.nftworlds.wallet.objects.payments.PeerToPeerPayment;
import com.nftworlds.wallet.rpcs.Ethereum;
import com.nftworlds.wallet.rpcs.Polygon;
import org.bukkit.Bukkit;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Convert;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class WRLD {
    private EthereumWRLDToken ethereumWRLDTokenContract;
    private PolygonWRLDToken polygonWRLDTokenContract;
    private boolean debug;

    public static final String TRANSFER_EVENT_TOPIC = Hash.sha3String("Transfer(address,address,uint256)");
    public static final String TRANSFER_REF_EVENT_TOPIC = Hash.sha3String("TransferRef(address,address,uint256,uint256)");

    public WRLD() {
        NFTWorlds nftWorlds = NFTWorlds.getInstance();
        Ethereum ethereumRPC = nftWorlds.getEthereumRPC();
        Polygon polygonRPC = nftWorlds.getPolygonRPC();
        Config config = nftWorlds.getNftConfig();
        Credentials credentials = Credentials.create(NFTWorlds.getInstance().getNftConfig().getServerPrivateKey()); //We're only reading so this can be anything
        NFTWorlds.getInstance().getLogger().info("Private key loaded corresponds to address: " + credentials.getAddress());

        /* The contract wrappers generated by web3j don't include a chainId and are rejected by the
        remote node with "only replay-protected (EIP-155) transactions allowed over RPC". We fix this
        making a TransactionManager with the correct Polygon chainId. */
        TransactionObjects.polygonTransactionManager = new FastRawTransactionManager(
                NFTWorlds.getInstance().getPolygonRPC().getPolygonWeb3j(),
                credentials, 137);

        debug = nftWorlds.getNftConfig().isDebug();

        this.ethereumWRLDTokenContract = EthereumWRLDToken.load(
                config.getEthereumWrldContract(),
                nftWorlds.getEthereumRPC().getEthereumWeb3j(),
                credentials,
                ethereumRPC.getGasProvider()
        );

        this.polygonWRLDTokenContract = PolygonWRLDToken.load(
                config.getPolygonWrldContract(),
                nftWorlds.getPolygonRPC().getPolygonWeb3j(),
                TransactionObjects.polygonTransactionManager,
                new StaticGasProvider(BigInteger.valueOf(90_100_000_000L), BigInteger.valueOf(9_000_000))
        );

        startPolygonPaymentListener();
    }

    /*
     * Public
     */

    public BigInteger getEthereumBalance(String walletAddress) throws Exception {
        return this.ethereumWRLDTokenContract.balanceOf(walletAddress).send();
    }

    public CompletableFuture<BigInteger> getEthereumBalanceAsync(String walletAddress) throws Exception {
        return this.ethereumWRLDTokenContract.balanceOf(walletAddress).sendAsync();
    }

    public BigInteger getPolygonBalance(String walletAddress) throws Exception {
        return this.polygonWRLDTokenContract.balanceOf(walletAddress).send();
    }

    public CompletableFuture<BigInteger> getPolygonBalanceAsync(String walletAddress) throws Exception {
        return this.polygonWRLDTokenContract.balanceOf(walletAddress).sendAsync();
    }

    public PolygonWRLDToken getPolygonWRLDTokenContract() {
        return this.polygonWRLDTokenContract;
    }

    /*
     * Private
     */

    private void startPolygonPaymentListener() {
        EthFilter transferFilter = new EthFilter(
                DefaultBlockParameterName.LATEST,
                DefaultBlockParameterName.LATEST,
                this.polygonWRLDTokenContract.getContractAddress()
        ).addOptionalTopics(WRLD.TRANSFER_REF_EVENT_TOPIC, WRLD.TRANSFER_EVENT_TOPIC);

        NFTWorlds.getInstance().getPolygonRPC().getPolygonWeb3j().ethLogFlowable(transferFilter).subscribe(log -> {
                    String eventHash = log.getTopics().get(0);

                    if (eventHash.equals(TRANSFER_REF_EVENT_TOPIC)) {
                        this.paymentListener_handleTransferRefEvent(log);
                    } else if (eventHash.equals(TRANSFER_EVENT_TOPIC)) {
                        this.paymentListener_handleTransferEvent(log);
                    }
                },
                error -> {
                    error.printStackTrace();
                });
    }

    private void paymentListener_handleTransferRefEvent(Log log) {
        if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Transfer initiated");

        List<String> topics = log.getTopics();
        List<Type> data = FunctionReturnDecoder.decode(log.getData(), PolygonWRLDToken.TRANSFERREF_EVENT.getNonIndexedParameters());
        TypeReference<Address> addressTypeReference = new TypeReference<Address>() {
        };

        Address fromAddress = (Address) FunctionReturnDecoder.decodeIndexedValue(topics.get(1), addressTypeReference);
        Address toAddress = (Address) FunctionReturnDecoder.decodeIndexedValue(topics.get(2), addressTypeReference);
        Uint256 amount = (Uint256) data.get(0);
        Uint256 ref = (Uint256) data.get(1);
        double received = Convert.fromWei(amount.getValue().toString(), Convert.Unit.ETHER).doubleValue();

        if (debug)
            NFTWorlds.getInstance().getLogger().log(Level.INFO, "Transfer of " + received + " $WRLD with refid " + ref.getValue().toString() + " from " + fromAddress.toString() + " to " + toAddress.toString());

        PaymentRequest paymentRequest = PaymentRequest.getPayment(ref, Network.POLYGON);

        if (paymentRequest != null) {
            if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Transfer found in payment requests");
            if (debug)
                NFTWorlds.getInstance().getLogger().log(Level.INFO, "Requested: " + paymentRequest.getAmount() + ", Received: " + received);

            if (paymentRequest.getAmount() == received) {
                if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Payment amount verified");

                if (!paymentRequest.isCanDuplicate()) PaymentRequest.getPaymentRequests().remove(paymentRequest);

                if (paymentRequest != null) {
                    if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Event fired");
                    Bukkit.getScheduler().runTask(NFTWorlds.getInstance(), new Runnable() {
                        @Override
                        public void run() {
                            new PlayerTransactEvent(
                                    Bukkit.getPlayer(paymentRequest.getAssociatedPlayer()),
                                    received,
                                    paymentRequest.getReason(),
                                    ref,
                                    paymentRequest.getPayload()
                            ).callEvent(); //TODO: Test if works for offline players
                        }
                    });
                }
            } else {
                NFTWorlds.getInstance().getLogger().log(
                        Level.WARNING,
                        "Payment with REFID " + ref.getValue().toString() + " was receive but amount was " + received + ". Expected " + paymentRequest.getAmount()
                );
            }
        } else { //Now let's check if this is a peer to peer payment
            PeerToPeerPayment peerToPeerPayment = PeerToPeerPayment.getPayment(ref, Network.POLYGON);

            if (peerToPeerPayment != null) {
                if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Transfer found in peer to peer payments");
                if (debug)
                    NFTWorlds.getInstance().getLogger().log(Level.INFO, "Requested: " + peerToPeerPayment.getAmount() + ", Received: " + received);

                if (peerToPeerPayment.getAmount() != received) {
                    peerToPeerPayment.setAmount(received);
                    if (debug)
                        NFTWorlds.getInstance().getLogger().log(Level.INFO, "Amount expected was different than amount received, value adjusted.");
                }

                PeerToPeerPayment.getPeerToPeerPayments().remove(peerToPeerPayment);

                if (peerToPeerPayment != null) {
                    if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Event fired");
                    Bukkit.getScheduler().runTask(NFTWorlds.getInstance(), new Runnable() {
                        @Override
                        public void run() {
                            new PeerToPeerPayEvent(
                                    Bukkit.getPlayer(peerToPeerPayment.getTo()),
                                    Bukkit.getPlayer(peerToPeerPayment.getFrom()),
                                    received,
                                    peerToPeerPayment.getReason(),
                                    ref
                            ).callEvent(); //TODO: Test if works for offline players
                        }
                    });
                }
            }
        }
    }

    private void paymentListener_handleTransferEvent(Log log) {
        if (debug) NFTWorlds.getInstance().getLogger().log(Level.INFO, "Payment detected");

        List<String> topics = log.getTopics();
        List<Type> data = FunctionReturnDecoder.decode(log.getData(), PolygonWRLDToken.TRANSFER_EVENT.getNonIndexedParameters());
        TypeReference<Address> addressTypeReference = new TypeReference<Address>() {
        };

        Address fromAddress = (Address) FunctionReturnDecoder.decodeIndexedValue(topics.get(1), addressTypeReference);
        Address toAddress = (Address) FunctionReturnDecoder.decodeIndexedValue(topics.get(2), addressTypeReference);
        Uint256 amount = (Uint256) data.get(0);
        double received = Convert.fromWei(amount.getValue().toString(), Convert.Unit.ETHER).doubleValue();

        if (debug)
            NFTWorlds.getInstance().getLogger().log(Level.INFO, "Transfer of " + received + " $WRLD from " + fromAddress.toString() + " to " + toAddress.toString() + " . Updating balances.");

        boolean foundSender = false;
        boolean foundReceiver = false;

        for (Map.Entry<UUID, NFTPlayer> entry : NFTPlayer.getPlayers().entrySet()) {
            NFTPlayer nftPlayer = entry.getValue();
            for (Wallet wallet : nftPlayer.getWallets()) {
                if (wallet.getAddress().equalsIgnoreCase(fromAddress.toString())) {
                    wallet.setPolygonWRLDBalance(wallet.getPolygonWRLDBalance() - received);
                    foundSender = true;
                }

                if (wallet.getAddress().equalsIgnoreCase(toAddress.toString())) {
                    wallet.setPolygonWRLDBalance(wallet.getPolygonWRLDBalance() + received);
                    foundReceiver = true;
                }

                if (foundSender && foundReceiver) {
                    break;
                }
            }

            if (foundSender && foundReceiver) {
                break;
            }
        }
    }
}
