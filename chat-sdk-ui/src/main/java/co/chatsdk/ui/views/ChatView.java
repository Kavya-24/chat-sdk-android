package co.chatsdk.ui.views;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.stfalcon.chatkit.commons.models.MessageContentType;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import co.chatsdk.core.dao.Message;
import co.chatsdk.core.dao.Thread;
import co.chatsdk.core.events.EventType;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.types.MessageSendProgress;
import co.chatsdk.core.utils.DisposableMap;
import co.chatsdk.ui.R;
import co.chatsdk.ui.chat.model.ImageMessageHolder;
import co.chatsdk.ui.chat.model.MessageHolder;
import co.chatsdk.ui.view_holders.IncomingImageMessageViewHolder;
import co.chatsdk.ui.view_holders.IncomingTextMessageViewHolder;
import co.chatsdk.ui.view_holders.OutcomingImageMessageViewHolder;
import co.chatsdk.ui.view_holders.OutcomingTextMessageViewHolder;
import co.chatsdk.ui.databinding.ViewChatBinding;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class ChatView extends LinearLayout implements MessagesListAdapter.OnLoadMoreListener {

    public interface Delegate {
        DisplayMetrics getDisplayMetrics();
        Thread getThread();
        void click(Message message);
    }

    protected MessagesListAdapter<MessageHolder> messagesListAdapter;

    protected HashMap<Message, MessageHolder> messageHolderHashMap = new HashMap<>();
    protected ArrayList<MessageHolder> messageHolders = new ArrayList<>();

    protected ViewChatBinding b;

    protected DisposableMap dm = new DisposableMap();

    protected PrettyTime prettyTime = new PrettyTime();

    protected Delegate delegate;

    public ChatView(Context context) {
        super(context);
    }

    public ChatView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ChatView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void initViews() {

        b = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.view_chat, this, true);

        LinearLayoutManager manager = new LinearLayoutManager(getContext());
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        b.messagesList.setLayoutManager(manager);

        IncomingTextMessageViewHolder.Payload holderPayload = new IncomingTextMessageViewHolder.Payload();
        holderPayload.avatarClickListener = user -> {
            ChatSDK.ui().startProfileActivity(getContext(), user.getEntityID());
        };

        MessageHolders holders = new MessageHolders()
                .setIncomingTextConfig(IncomingTextMessageViewHolder.class, R.layout.chatkit_item_incoming_text_message, holderPayload)
                .setOutcomingTextConfig(OutcomingTextMessageViewHolder.class, R.layout.chatkit_item_outcoming_text_message, null)
                .setIncomingImageConfig(IncomingImageMessageViewHolder.class, R.layout.chatkit_item_incoming_image_message, null)
                .setOutcomingImageConfig(OutcomingImageMessageViewHolder.class, R.layout.chatkit_item_outcoming_image_message);

        messagesListAdapter = new MessagesListAdapter<>(ChatSDK.currentUserID(), holders, (imageView, url, payload) -> {
            if (url == null || url.isEmpty()) {
                if (payload == null) {
                    imageView.setImageResource(R.drawable.icn_100_profile);
                } else if (payload instanceof ImageMessageHolder) {
                    imageView.setImageResource(R.drawable.icn_200_image_message_placeholder);
                }
            } else {

                RequestCreator request = Picasso.get().load(url)
                        .resize(maxImageWidth(), maxImageWidth());

                if (payload == null) {
                    request.placeholder(R.drawable.icn_100_profile);
                } else if (payload instanceof ImageMessageHolder) {
                    request.error(R.drawable.icn_200_image_message_error);
                    request.placeholder(R.drawable.icn_200_image_message_placeholder);
                }

                request.into(imageView);
            }
        });

        messagesListAdapter.setLoadMoreListener(this);
        messagesListAdapter.setDateHeadersFormatter(date -> prettyTime.format(date));

        messagesListAdapter.setOnMessageClickListener(holder -> {
            if (holder instanceof MessageContentType.Image) {

                MessageContentType.Image content = (MessageContentType.Image) holder;

                if (content.getImageUrl() != null) {
                    delegate.click(holder.getMessage());
                }
            }
        });

        b.messagesList.setAdapter(messagesListAdapter);

        addListeners();
        onLoadMore(0, 0);

    }

    protected void addListeners() {
        // Add the event listeners
        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.MessageAdded))
                .filter(NetworkEvent.filterThreadEntityID(delegate.getThread().getEntityID()))
                .subscribe(networkEvent -> {
                    Message message = networkEvent.message;
                    addMessageToStartOrUpdate(message);
                    message.markRead();
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.MessageReadReceiptUpdated))
                .filter(NetworkEvent.filterThreadEntityID(delegate.getThread().getEntityID()))
                .subscribe(networkEvent -> {

                    Message message = networkEvent.message;

                    if (ChatSDK.readReceipts() != null && message.getSender().isMe()) {
                        addMessageToStartOrUpdate(message);
                    }
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.MessageRemoved))
                .filter(NetworkEvent.filterThreadEntityID(delegate.getThread().getEntityID()))
                .subscribe(networkEvent -> {
                    removeMessage(networkEvent.message);
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.MessageSendStatusChanged))
                .filter(NetworkEvent.filterThreadEntityID(delegate.getThread().getEntityID()))
                .subscribe(networkEvent -> {

                    MessageSendProgress progress = networkEvent.getMessageSendProgress();
                    addMessageToStartOrUpdate(progress.message, progress);
                }));
    }

    protected int maxImageWidth() {
        // Prevent overly big messages in landscape mode
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            return Math.round(delegate.getDisplayMetrics().widthPixels);
        } else {
            return Math.round(delegate.getDisplayMetrics().heightPixels);
        }
    }

    public void clearSelection() {
        messagesListAdapter.unselectAllItems();
    }

    public List<Message> getSelectedMessages() {
        return MessageHolder.toMessages(messagesListAdapter.getSelectedMessages());
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {
        Date loadFromDate = null;
        if (totalItemsCount != 0) {
            // This list has the newest first
            loadFromDate = messageHolders.get(messageHolders.size()-1).getCreatedAt();
        }
        dm.add(ChatSDK.thread()
                .loadMoreMessagesForThread(loadFromDate, delegate.getThread(), true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::addMessagesToEnd));
    }

    public void removeMessage(Message message) {
        MessageHolder holder = messageHolderHashMap.get(message);

        if (holder != null) {
            messagesListAdapter.delete(holder);
            messageHolders.remove(holder);
            messageHolderHashMap.remove(message);
        }

        updatePrevious(message);
        updateNext(message);
    }

    public void updatePrevious(Message message) {
        MessageHolder holder = previous(message);
        if (holder != null) {
            messagesListAdapter.update(holder);
        }
    }

    public MessageHolder previous(Message message) {
        Message previous = message.getPreviousMessage();
        if (previous != null) {
            return messageHolderHashMap.get(previous);
        }
        return null;
    }

    public void updateNext(Message message) {
        MessageHolder holder = next(message);
        if (holder != null) {
            messagesListAdapter.update(holder);
        }
    }

    public MessageHolder next(Message message) {
        Message next = message.getNextMessage();
        if (next != null) {
            return messageHolderHashMap.get(next);
        }
        return null;
    }

    public void addMessageToStartOrUpdate(Message message) {
        addMessageToStartOrUpdate(message, null);
    }

    public void addMessageToStartOrUpdate(Message message, MessageSendProgress progress) {
        MessageHolder holder = messageHolderHashMap.get(message);

        if (holder == null) {
            holder = MessageHolder.fromMessage(message);

            messageHolders.add(0, holder);
            messageHolderHashMap.put(holder.getMessage(), holder);

            // This means that we only scroll down if we were already at the bottom
            // it can be annoying if you have scrolled up and then a new message
            // comes in and scrolls the screen down
            boolean scroll = message.getSender().isMe();

            RecyclerView.LayoutManager layoutManager = b.messagesList.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager llm = (LinearLayoutManager) layoutManager;

                if (llm.findLastVisibleItemPosition() > messageHolders.size() - 5) {
                    scroll = true;
                }
            }
            messagesListAdapter.addToStart(holder, scroll);

            // Update the previous holder so that we can hide the
            // name if necessary
            updatePrevious(message);

        } else {
            holder.setProgress(progress);
            messagesListAdapter.update(holder);
        }
    }

    public void addMessagesToEnd(List<Message> messages) {
        // Check to see if the holders already exist
        ArrayList<MessageHolder> holders = new ArrayList<>();
        for (Message message: messages) {
            MessageHolder holder = messageHolderHashMap.get(message);
            if (holder == null) {
                holder = MessageHolder.fromMessage(message);
                messageHolderHashMap.put(message, holder);
                holders.add(holder);
            }
        }
        messageHolders.addAll(holders);
        messagesListAdapter.addToEnd(holders, false);
    }

    public void notifyDataSetChanged() {
        messagesListAdapter.notifyDataSetChanged();
    }

    public void clear() {
        if (messagesListAdapter != null) {
            messageHolderHashMap.clear();
            messageHolders.clear();
            messagesListAdapter.clear();
        }
    }

    public void copySelectedMessagesText(Context context, MessagesListAdapter.Formatter<MessageHolder> formatter, boolean reverse) {
        messagesListAdapter.copySelectedMessagesText(context, formatter, reverse);
    }

    public void enableSelectionMode(MessagesListAdapter.SelectionListener selectionListener) {
        messagesListAdapter.enableSelectionMode(selectionListener);
    }

    public void filter(String filter) {
        if (filter == null || filter.isEmpty()) {
            clearFilter();
        } else {
            filter = filter.trim();

            ArrayList<MessageHolder> filtered = new ArrayList<>();
            for (MessageHolder holder: messageHolders) {
                if (holder.getText().toLowerCase().contains(filter)) {
                    filtered.add(holder);
                }
            }

            messagesListAdapter.clear();
            messagesListAdapter.addToEnd(filtered, true);
        }
    }

    public void clearFilter() {
        messagesListAdapter.clear();
        messagesListAdapter.addToEnd(messageHolders, true);
    }
}