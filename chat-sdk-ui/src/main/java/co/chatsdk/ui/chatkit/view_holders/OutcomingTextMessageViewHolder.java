package co.chatsdk.ui.chatkit.view_holders;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.stfalcon.chatkit.messages.MessageHolders;

import java.lang.ref.WeakReference;

import co.chatsdk.core.dao.Message;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.ui.R;
import co.chatsdk.ui.chatkit.binders.MessageBinder;
import co.chatsdk.ui.chatkit.binders.ReadStatusViewBinder;
import co.chatsdk.ui.chatkit.binders.ReplyViewBinder;
import co.chatsdk.ui.chatkit.model.MessageHolder;

public class OutcomingTextMessageViewHolder
        extends MessageHolders.OutcomingTextMessageViewHolder<MessageHolder> {

    protected ImageView readStatus;
    protected View replyView;
    protected ImageView replyImageView;
    protected TextView replyTextView;

    public OutcomingTextMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);

        readStatus = itemView.findViewById(R.id.readStatus);
        replyView = itemView.findViewById(R.id.replyView);
        replyImageView = itemView.findViewById(R.id.replyImageView);
        replyTextView = itemView.findViewById(R.id.replyTextView);
    }

    @Override
    public void onBind(MessageHolder holder) {
        super.onBind(holder);

        ReplyViewBinder.onBind(replyView, replyTextView, replyImageView, holder);
        ReadStatusViewBinder.onBind(readStatus, holder);
        MessageBinder.onBindSendStatus(time, holder);

//        time.setVisibility(View.VISIBLE);
//        if (holder.getStatus() == MessageSendStatus.Sent) {
//            Message nextMessage = message.getNextMessage();
//
//            // Hide the time if it's the same as the next message
//            if (nextMessage != null && format.format(message.getDate().toDate()).equals(format.format(nextMessage.getDate().toDate()))) {
//                time.setVisibility(View.GONE);
//            }
//        } else {
//        }



    }


}
