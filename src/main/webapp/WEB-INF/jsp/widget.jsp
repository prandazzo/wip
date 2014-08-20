<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<jsp:useBean class="java.lang.String" id="proxyUrl" scope="request" />

<style>
  .widget {
      width: 100%;
      overflow:hidden;
      border: 0px;
  }
</style>

<iframe class="widget" scrolling="no" onload="resizeIframe(this)" seamless="seamless" src="<%=proxyUrl%>"></iframe>

<aui:script>

function resizeIframe(iframe) {
	iframe.height = iframe.contentWindow.document.body.scrollHeight + "px";
}

AUI().use('aui-base, event-resize', function(A) {
	
	A.one(window).on('resize', function(event) {
		var widget = A.one('.widget')._node;
		resizeIframe(widget);
	});
});

</aui:script>
