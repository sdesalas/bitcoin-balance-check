package com.jurajkusnier.bitcoinwalletbalance.ui.detail

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.jurajkusnier.bitcoinwalletbalance.R
import com.jurajkusnier.bitcoinwalletbalance.data.db.WalletRecordView
import com.jurajkusnier.bitcoinwalletbalance.data.model.ExchangeRate
import com.jurajkusnier.bitcoinwalletbalance.di.ViewModelFactory
import com.jurajkusnier.bitcoinwalletbalance.ui.edit.EditDialog
import com.jurajkusnier.bitcoinwalletbalance.ui.edit.EditDialogInterface
import com.jurajkusnier.bitcoinwalletbalance.utils.format
import com.jurajkusnier.bitcoinwalletbalance.utils.sathoshiToBTCstring
import com.squareup.moshi.Moshi
import dagger.android.AndroidInjection
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.detail_fragment.*
import kotlinx.android.synthetic.main.detail_fragment.view.*
import javax.inject.Inject


class DetailFragment: DaggerFragment(), AppBarLayout.OnOffsetChangedListener, EditDialogInterface {

    override fun showEditDialog(address: String, nickname: String) {
        EditDialog.newInstance(address,nickname).show(fragmentManager, EditDialog.TAG)
    }

    private lateinit var viewModel:DetailViewModel
    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var moshi: Moshi

    companion object {
        val TAG = DetailFragment::class.java.simpleName
        val WALLET_ID = "WALLET_ID"

        fun newInstance(walletID:String):DetailFragment {
            val detailFragment =DetailFragment()
            val bundle = Bundle()
            bundle.putString(WALLET_ID, walletID)
            detailFragment.arguments = bundle

            return detailFragment
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        errorSnackbar?.dismiss()
    }

    override fun onAttach(context: Context?) {
        //DI activity injection first
        AndroidInjection.inject(activity)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {

        val view = inflater.inflate(R.layout.detail_fragment, container, false)

        //Let's make detail view look nicer on every screen
        context?.let {
            val displayMetrics = it.resources.displayMetrics
            view.detailCardView.minimumHeight = displayMetrics.heightPixels
        }

        return view
    }

    var _walletRecord:WalletRecordView? = null
    var _exchangeRate:ExchangeRate? = null

    private fun getBitcoinPriceInMoney():String {
        _exchangeRate?.let { rate ->
            _walletRecord?.let {
                return "${(rate.price * it.finalBalance / 100_000_000).format(2)} ${rate.currency}"
            }
        }

        return ""
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //ViewModel
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(DetailViewModel::class.java)

        val walletID = arguments?.getString(WALLET_ID)
        if (walletID != null) {
            viewModel.initViewModel(walletID)
        }

//        viewModel.initViewModel("14FWSLwNWHCQjsA2teKSKeiaxA3F5kH1tZ") // Empty wallet
//        viewModel.initViewModel("14s299LGRmSX5dxtcuY4gqUgn2tW3nCz8m") // Not empty wallet
//        viewModel.initViewModel("1r8JvHjYFiFDNdrDBW1iqDp8pmoaWuSaz") // Lot of transactions



        textWalletID.text = walletID

        //UI initialization
        val thisContext = activity
        if (thisContext != null) {
            colorAccent = ContextCompat.getColor(thisContext, R.color.colorAccent)
            initView(thisContext)
        }

        viewModel.liveExchangeRate.observe(this, Observer {

            _exchangeRate = it
            textFinalBalanceMoney.text = getBitcoinPriceInMoney()

        })

        viewModel.walletDetail.observe(this, Observer walletRecordObserver@{

            _walletRecord = it

            optionsMenu?.findItem(R.id.menu_favourite)?.isVisible = it?.favourite == false
            optionsMenu?.findItem(R.id.menu_unfavourite)?.isVisible = it?.favourite == true
            optionsMenu?.findItem(R.id.menu_edit)?.isVisible = (it != null)

            textFinalBalanceCrypto.text = sathoshiToBTCstring(it?.finalBalance ?: 0)
            textTotalReceived.text = sathoshiToBTCstring(it?.totalReceived ?: 0)
            textTotalSent.text = sathoshiToBTCstring(it?.totalSent ?: 0)

            if (thisContext != null && it != null) {
                    recyclerViewTransactions.adapter = TransactionListAdapter(it.address, it.transactions, thisContext)
                    recyclerViewTransactions.adapter.notifyDataSetChanged()
                    recyclerViewTransactions.isNestedScrollingEnabled = false
            }

            if (it == null || it.transactions.isEmpty()) {
                textViewNoTransaction.visibility = View.VISIBLE
            } else {
                textViewNoTransaction.visibility = View.GONE
            }

            textFinalBalanceMoney.text = getBitcoinPriceInMoney()
        })

        viewModel.loadingState.observe(this, Observer<DetailViewModel.LoadingState> {
            swiperefresh.isRefreshing = (it == DetailViewModel.LoadingState.LOADING)
            optionsMenu?.findItem(R.id.menu_refresh)?.isEnabled = (it != DetailViewModel.LoadingState.LOADING)

            when (it) {
                DetailViewModel.LoadingState.ERROR -> showErrorSnackbar(false)
                DetailViewModel.LoadingState.ERROR_OFFLINE -> showErrorSnackbar(true)
                else -> hideErrorShackbar()
            }
        })

        setHasOptionsMenu(true)
    }

    private var errorSnackbar: Snackbar? = null
    private var colorAccent:Int = Color.RED

    private fun showErrorSnackbar(isOffline: Boolean) {

        errorSnackbar = Snackbar.make(detailLayout,getString(
                if (isOffline) {
                    R.string.offline_error
                } else {
                    R.string.network_connection_error
                }
                ),Snackbar.LENGTH_INDEFINITE)
        errorSnackbar?.view?.setBackgroundColor(colorAccent)
        errorSnackbar?.show()
    }

    private fun hideErrorShackbar() {
        errorSnackbar?.dismiss()
    }

    private fun initView(context: Context) {

        if (context is AppCompatActivity) {
            context.setSupportActionBar(toolbarResults)
            context.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            context.supportActionBar?.setDisplayShowHomeEnabled(true)
            context.supportActionBar?.title=""
        }

        appbar.addOnOffsetChangedListener(this)

        recyclerViewTransactions.layoutManager = LinearLayoutManager(context)
        recyclerViewTransactions.setHasFixedSize(false)
        recyclerViewTransactions.adapter = null

        textInfo.visibility = View.GONE

        val mDividerItemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        recyclerViewTransactions.addItemDecoration(mDividerItemDecoration)

        swiperefresh.setOnRefreshListener {
            viewModel.loadWalletDetails()
        }
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
        if (appBarLayout == null) return

        val range= appBarLayout.totalScrollRange.toFloat()

        when {
            Math.abs(verticalOffset) == range.toInt() -> {
                // Collapsed
                newTitle.alpha=1.0f
                swiperefresh.isEnabled = false
            }
            verticalOffset == 0 -> {
                // Expanded
                newTitle.alpha=0.0f
                swiperefresh.isEnabled = true
            }
            else -> {
                // Somewhere in between
                swiperefresh.isEnabled = false
                val value = Math.abs(verticalOffset/range) - 0.5f
                newTitle.alpha = value * 2f
            }
        }
    }

    var optionsMenu: Menu? = null

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        optionsMenu = menu
        inflater?.inflate(R.menu.detail_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when(item?.itemId) {
            R.id.menu_refresh -> {
                viewModel.loadWalletDetails()
                true
            }
            R.id.menu_favourite -> {
                _walletRecord?.let {
                    viewModel.favouriteRecord()
                }
                true
            }
            R.id.menu_unfavourite-> {
                _walletRecord?.let {
                    viewModel.unfavouriteRecord()
                }
                true
            }
            R.id.menu_edit-> {
                _walletRecord?.let {
                    showEditDialog(it.address,it.nickname)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }


    }

}