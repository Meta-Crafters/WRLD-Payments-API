package com.nftworlds.wallet.config;

import com.nftworlds.wallet.NFTWorlds;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;

import java.util.logging.Level;

@Getter
public class Config {
    private String polygonHttpsRpc;
    private String ethereumHttpsRpc;
    private String serverWalletAddress;
    private String serverPrivateKey;
    private String hotwalletHttpsEndpoint;

    private String polygonPlayerContract;
    private String polygonWrldContract;
    private String ethereumWrldContract;

    private int linkTimeout; //Link timeout in minutes

    private boolean debug;
    private boolean useHotwalletForOutgoingTransactions;

    public void registerConfig() {
        NFTWorlds wallet = NFTWorlds.getInstance();
        FileConfiguration config = wallet.getConfig();
        config.options().copyDefaults(true);
        wallet.saveConfig();

        this.polygonHttpsRpc = config.getString("polygon_https_rpc");
        this.ethereumHttpsRpc = config.getString("ethereum_https_rpc");
        this.hotwalletHttpsEndpoint = config.getString("hotwallet_https_endpoint");

        if (this.polygonHttpsRpc.isEmpty() || this.ethereumHttpsRpc.isEmpty()) {
            NFTWorlds.getInstance().getLogger().log(Level.SEVERE, "polygon_https_rpc and ethereum_https_rpc are not set! Please " +
                    "set an HTTPS endpoint for an Ethereum and Polygon node. We recommend using Alchemy or Infura which" +
                    " will allow you to get started in five minutes!");
            NFTWorlds.getInstance().getLogger().log(Level.SEVERE, "Shutting down server. You must configure polygon_https_rpc and " +
                    "ethereum_https_rpc to use WRLD-Payments-API. Please see docs at " +
                    "https://dev.nftworlds.com/payments/wrld-payments-api#configuring-ethereum-and-polygon-rpc-endpoints");
            System.exit(-1);
        }

        String serverWalletPrivateKey = config.getString("server_wallet_private_key");
        if (serverWalletPrivateKey != null && !serverWalletPrivateKey.equals("")) {
            NFTWorlds.getInstance().getLogger().warning("A private key has been set in the plugin config! Only install " +
                    "plugins you trust. ");
            this.serverPrivateKey = config.getString("server_wallet_private_key");
            useHotwalletForOutgoingTransactions = false;
        } else {
            this.serverPrivateKey = "0x0000000000000000000000000000000000000000000000000000000000000000";
        }

        if (hotwalletHttpsEndpoint != null && !hotwalletHttpsEndpoint.equals("")) {
            NFTWorlds.getInstance().getLogger().info("Hotwallet API for outgoing transactions enabled!");
            useHotwalletForOutgoingTransactions = true;
        }


        String address = config.getString("server_wallet_address");
        if (validateAddress(address, "Server Wallet Address")) {
            this.serverWalletAddress = address;
        }

        String polygonPlayerContract = config.getString("contracts.polygon_player_contract");
        if (validateAddress(polygonPlayerContract, "Polygon Player Contract")) {
            this.polygonPlayerContract = polygonPlayerContract;
        }

        String polygonWrldContract = config.getString("contracts.polygon_wrld_contract");
        if (validateAddress(polygonWrldContract, "Polygon WRLD Contract")) {
            this.polygonWrldContract = polygonWrldContract;
        }

        String ethereumWrldContract = config.getString("contracts.ethereum_wrld_contract");
        if (validateAddress(ethereumWrldContract, "Ethereum WRLD Contract")) {
            this.ethereumWrldContract = ethereumWrldContract;
        }

        this.linkTimeout = config.getInt("link-timeout");
        this.debug = config.getBoolean("debug");
    }

    private boolean validateAddress(String address, String name) {
        if (!WalletUtils.isValidAddress(address) || !Keys.toChecksumAddress(address).equalsIgnoreCase(address)) {
            NFTWorlds.getInstance().getLogger().log(Level.WARNING, name + " is an invalid format. Check config.yml.");
            Bukkit.getServer().getPluginManager().disablePlugin(NFTWorlds.getInstance());
            return false;
        }

        return true;
    }

}
